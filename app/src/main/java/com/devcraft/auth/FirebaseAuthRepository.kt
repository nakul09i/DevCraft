package com.devcraft.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Minimal authenticated identity kept locally. Never a password, never a token. */
data class AuthUser(
    val uid: String,
    val phoneNumber: String? = null,
    val email: String? = null,
) {
    val displayIdentity: String get() = phoneNumber ?: email ?: uid.take(8)
}

sealed interface OtpSendResult {
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken?,
    ) : OtpSendResult

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
    INVALID_EMAIL,
    WRONG_PASSWORD,
    WEAK_PASSWORD,
    EMAIL_ALREADY_IN_USE,
    NO_SUCH_USER,
    /** Phone auth needs the app's SHA-1 registered in the Firebase console. */
    MISSING_APP_CREDENTIAL,
    UNKNOWN,
}

class AuthException(val failure: AuthFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)

/**
 * Firebase Authentication for DevCraft: phone/OTP and email/password.
 *
 * Deliberately optional. [isAvailable] is false when no Firebase config was
 * bundled, and the app then runs entirely offline rather than showing a login
 * wall it cannot satisfy. Verification needs network - inherent to both SMS and
 * password checks - but once signed in Firebase caches the session on disk, so
 * reopening offline keeps the user authenticated with no request.
 */
class FirebaseAuthRepository(context: Context) {

    private val firebaseReady: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? = if (firebaseReady) {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    } else {
        null
    }

    val isAvailable: Boolean get() = auth != null

    /** Cached session; readable with no network. */
    fun currentUser(): AuthUser? = auth?.currentUser?.let {
        AuthUser(it.uid, it.phoneNumber, it.email)
    }

    fun signOut() = auth?.signOut()

    // ---------------- Email / password ----------------
    // No SHA-1 required, so this works as soon as the provider is enabled.

    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(AuthException(AuthFailure.NOT_CONFIGURED))
        validateEmailAndPassword(email, password)?.let { return Result.failure(AuthException(it)) }

        return runCatching {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: throw AuthException(AuthFailure.UNKNOWN)
            AuthUser(user.uid, user.phoneNumber, user.email)
        }.recoverCatching { throw AuthException(classify(it), it) }
    }

    suspend fun createEmailAccount(email: String, password: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(AuthException(AuthFailure.NOT_CONFIGURED))
        validateEmailAndPassword(email, password)?.let { return Result.failure(AuthException(it)) }

        return runCatching {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: throw AuthException(AuthFailure.UNKNOWN)
            AuthUser(user.uid, user.phoneNumber, user.email)
        }.recoverCatching { throw AuthException(classify(it), it) }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(AuthException(AuthFailure.NOT_CONFIGURED))
        if (!isPlausibleEmail(email)) return Result.failure(AuthException(AuthFailure.INVALID_EMAIL))
        return runCatching {
            // await() on Task<Void> yields Void?, so normalise to Unit.
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Unit
        }.recoverCatching { throw AuthException(classify(it), it) }
    }

    // ---------------- Phone / OTP ----------------

    suspend fun sendOtp(
        activity: Activity,
        e164Phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    ): OtpSendResult {
        val firebaseAuth = auth ?: return OtpSendResult.Failed(AuthFailure.NOT_CONFIGURED)
        if (!isPlausibleE164(e164Phone)) return OtpSendResult.Failed(AuthFailure.INVALID_PHONE)
        return requestOtp(firebaseAuth, activity, e164Phone, resendToken)
    }

    suspend fun verifyOtp(verificationId: String, code: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(AuthException(AuthFailure.NOT_CONFIGURED))
        if (code.length !in 4..8 || code.any { !it.isDigit() }) {
            return Result.failure(AuthException(AuthFailure.INVALID_CODE))
        }
        return runCatching {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user ?: throw AuthException(AuthFailure.UNKNOWN)
            AuthUser(user.uid, user.phoneNumber, user.email)
        }.recoverCatching { throw AuthException(classify(it), it) }
    }

    private suspend fun requestOtp(
        firebaseAuth: FirebaseAuth,
        activity: Activity,
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): OtpSendResult = suspendCancellableCoroutine { continuation ->
        var settled = false
        fun settle(result: OtpSendResult) {
            if (!settled) {
                settled = true
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            }
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) =
                settle(OtpSendResult.CodeSent(id, token))

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener { res ->
                        val u = res.user
                        if (u != null) {
                            settle(OtpSendResult.AutoVerified(AuthUser(u.uid, u.phoneNumber, u.email)))
                        } else {
                            settle(OtpSendResult.Failed(AuthFailure.UNKNOWN))
                        }
                    }
                    .addOnFailureListener { settle(OtpSendResult.Failed(classify(it))) }
            }

            override fun onVerificationFailed(e: FirebaseException) =
                settle(OtpSendResult.Failed(classify(e)))
        }

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phone)
            .setTimeout(OTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let { setForceResendingToken(it) } }
            .build()

        runCatching { PhoneAuthProvider.verifyPhoneNumber(options) }
            .onFailure { settle(OtpSendResult.Failed(classify(it))) }
    }

    private fun validateEmailAndPassword(email: String, password: String): AuthFailure? = when {
        !isPlausibleEmail(email) -> AuthFailure.INVALID_EMAIL
        password.length < MIN_PASSWORD_LENGTH -> AuthFailure.WEAK_PASSWORD
        else -> null
    }

    companion object {
        const val OTP_TIMEOUT_SECONDS = 60L
        const val MIN_PASSWORD_LENGTH = 6

        /** Loose E.164 check: '+', then 8-15 digits. */
        fun isPlausibleE164(value: String): Boolean {
            if (!value.startsWith("+")) return false
            val digits = value.drop(1)
            return digits.length in 8..15 && digits.all { it.isDigit() }
        }

        /**
         * Deliberately permissive: one '@', a dot in the domain, no whitespace.
         * Firebase is the real authority; this only catches obvious typos before
         * spending a network round trip.
         */
        fun isPlausibleEmail(value: String): Boolean {
            val v = value.trim()
            if (v.isEmpty() || v.any { it.isWhitespace() }) return false
            val parts = v.split("@")
            if (parts.size != 2) return false
            val (local, domain) = parts
            return local.isNotEmpty() && domain.contains('.') &&
                !domain.startsWith('.') && !domain.endsWith('.')
        }

        fun classify(t: Throwable): AuthFailure = when {
            t is AuthException -> t.failure
            t is FirebaseNetworkException -> AuthFailure.NETWORK_UNAVAILABLE
            t is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_ATTEMPTS
            t is FirebaseAuthWeakPasswordException -> AuthFailure.WEAK_PASSWORD
            t is FirebaseAuthUserCollisionException -> AuthFailure.EMAIL_ALREADY_IN_USE
            t is FirebaseAuthInvalidUserException -> AuthFailure.NO_SUCH_USER
            t is FirebaseAuthInvalidCredentialsException -> when {
                t.message?.contains("expired", ignoreCase = true) == true -> AuthFailure.CODE_EXPIRED
                t.message?.contains("password", ignoreCase = true) == true -> AuthFailure.WRONG_PASSWORD
                t.message?.contains("email", ignoreCase = true) == true -> AuthFailure.INVALID_EMAIL
                else -> AuthFailure.INVALID_CODE
            }
            // Surfaced when the app's SHA-1 is not registered for this project.
            t.message?.contains("APP_NOT_AUTHORIZED", ignoreCase = true) == true ->
                AuthFailure.MISSING_APP_CREDENTIAL
            t.message?.contains("INVALID_APP_CREDENTIAL", ignoreCase = true) == true ->
                AuthFailure.MISSING_APP_CREDENTIAL
            t.message?.contains("network", ignoreCase = true) == true ->
                AuthFailure.NETWORK_UNAVAILABLE
            else -> AuthFailure.UNKNOWN
        }
    }
}
