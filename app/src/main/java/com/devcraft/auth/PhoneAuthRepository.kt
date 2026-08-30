package com.devcraft.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Minimal authenticated identity we keep locally. Never a password, never a token. */
data class AuthUser(val uid: String, val phoneNumber: String?)

sealed interface OtpSendResult {
    /** Code dispatched; hold [verificationId] for the verify step. */
    data class CodeSent(val verificationId: String, val resendToken: PhoneAuthProvider.ForceResendingToken?) : OtpSendResult
    /** Auto-retrieval or instant validation signed the user in with no code entry. */
    data class AutoVerified(val user: AuthUser) : OtpSendResult
    data class Failed(val reason: AuthFailure) : OtpSendResult
}

enum class AuthFailure {
    NOT_CONFIGURED,
    INVALID_PHONE,
    INVALID_CODE,
    CODE_EXPIRED,
    TOO_MANY_ATTEMPTS,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
}

/**
 * Firebase phone/OTP authentication.
 *
 * Deliberately optional: [isAvailable] is false when no Firebase config was
 * bundled, and the app must then run entirely offline rather than showing a
 * login wall it cannot satisfy. Verification itself needs network - that is
 * inherent to SMS - but once signed in, Firebase caches the session locally so
 * reopening offline keeps the user authenticated.
 */
class PhoneAuthRepository(context: Context) {

    private val firebaseReady: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? = if (firebaseReady) {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    } else {
        null
    }

    val isAvailable: Boolean get() = auth != null

    /** Cached session; readable with no network. */
    fun currentUser(): AuthUser? = auth?.currentUser?.let { AuthUser(it.uid, it.phoneNumber) }

    fun signOut() {
        auth?.signOut()
    }

    /**
     * Starts verification for an E.164 number (e.g. +919876543210).
     * [resendToken] non-null forces a resend of a previously sent code.
     */
    suspend fun sendOtp(
        activity: Activity,
        e164Phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    ): OtpSendResult {
        val firebaseAuth = auth ?: return OtpSendResult.Failed(AuthFailure.NOT_CONFIGURED)
        if (!isPlausibleE164(e164Phone)) return OtpSendResult.Failed(AuthFailure.INVALID_PHONE)

        return suspendCancellableSend(firebaseAuth, activity, e164Phone, resendToken)
    }

    /** Exchanges the typed code for a session. */
    suspend fun verifyOtp(verificationId: String, code: String): Result<AuthUser> {
        val firebaseAuth = auth
            ?: return Result.failure(AuthException(AuthFailure.NOT_CONFIGURED))
        if (code.length !in 4..8 || code.any { !it.isDigit() }) {
            return Result.failure(AuthException(AuthFailure.INVALID_CODE))
        }
        return runCatching {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw AuthException(AuthFailure.UNKNOWN)
            AuthUser(user.uid, user.phoneNumber)
        }.recoverCatching { throw AuthException(classify(it), it) }
    }

    private suspend fun suspendCancellableSend(
        firebaseAuth: FirebaseAuth,
        activity: Activity,
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): OtpSendResult = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        var settled = false
        fun settle(result: OtpSendResult) {
            if (!settled) {
                settled = true
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            }
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                settle(OtpSendResult.CodeSent(id, token))
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Instant/auto retrieval. Complete the sign-in ourselves.
                firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener { res ->
                        val u = res.user
                        if (u != null) settle(OtpSendResult.AutoVerified(AuthUser(u.uid, u.phoneNumber)))
                        else settle(OtpSendResult.Failed(AuthFailure.UNKNOWN))
                    }
                    .addOnFailureListener { settle(OtpSendResult.Failed(classify(it))) }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                settle(OtpSendResult.Failed(classify(e)))
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phone)
            .setTimeout(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let { optionsBuilder.setForceResendingToken(it) }

        runCatching { PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build()) }
            .onFailure { settle(OtpSendResult.Failed(classify(it))) }
    }

    companion object {
        const val OTP_TIMEOUT_SECONDS = 60L

        /** Loose E.164 check: '+', country code, 8-15 digits total. */
        fun isPlausibleE164(value: String): Boolean {
            if (!value.startsWith("+")) return false
            val digits = value.drop(1)
            return digits.length in 8..15 && digits.all { it.isDigit() }
        }

        fun classify(t: Throwable): AuthFailure = when {
            t is AuthException -> t.failure
            t is FirebaseNetworkException -> AuthFailure.NETWORK_UNAVAILABLE
            t is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_ATTEMPTS
            t is FirebaseAuthInvalidCredentialsException -> {
                if (t.message?.contains("expired", ignoreCase = true) == true) AuthFailure.CODE_EXPIRED
                else AuthFailure.INVALID_CODE
            }
            t.message?.contains("network", ignoreCase = true) == true -> AuthFailure.NETWORK_UNAVAILABLE
            else -> AuthFailure.UNKNOWN
        }
    }
}

class AuthException(val failure: AuthFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)
