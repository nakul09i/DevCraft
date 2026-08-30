package com.devcraft.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcraft.auth.AuthFailure
import com.devcraft.auth.AuthUser
import com.devcraft.auth.FirebaseAuthRepository
import com.devcraft.auth.OtpSendResult
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { CHOOSE, PHONE, CODE, EMAIL }

data class AuthUiState(
    /** False when no Firebase config is bundled; the app then skips login entirely. */
    val firebaseAvailable: Boolean = false,
    val user: AuthUser? = null,
    val step: AuthStep = AuthStep.CHOOSE,
    val countryCode: String = "+91",
    val phone: String = "",
    val code: String = "",
    val email: String = "",
    val password: String = "",
    /** Email pane toggles between signing in and creating an account. */
    val creatingAccount: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val resendSeconds: Int = 0,
    /** Merchant chose to work offline without signing in. */
    val offlineBypass: Boolean = false,
) {
    val e164: String get() = countryCode + phone.filter { it.isDigit() }

    val canEnterApp: Boolean get() = !firebaseAvailable || user != null || offlineBypass
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FirebaseAuthRepository(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AuthUiState(
            firebaseAvailable = repo.isAvailable,
            user = repo.currentUser(),
            offlineBypass = prefs.getBoolean(KEY_BYPASS, false),
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdownJob: Job? = null

    // ---- field edits ----
    fun setCountryCode(v: String) = _state.update { it.copy(countryCode = v, error = null) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() }, error = null) }
    fun setCode(v: String) = _state.update { it.copy(code = v.filter { c -> c.isDigit() }.take(6), error = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v.trim(), error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    // ---- navigation between panes ----
    fun chooseMethod(step: AuthStep) = _state.update { it.copy(step = step, error = null, notice = null) }

    fun backToChooser() {
        countdownJob?.cancel()
        _state.update {
            it.copy(step = AuthStep.CHOOSE, code = "", error = null, notice = null, resendSeconds = 0)
        }
    }

    fun backToPhone() {
        countdownJob?.cancel()
        _state.update { it.copy(step = AuthStep.PHONE, code = "", error = null, resendSeconds = 0) }
    }

    fun toggleCreatingAccount() =
        _state.update { it.copy(creatingAccount = !it.creatingAccount, error = null, notice = null) }

    /** Explicit "use offline without signing in", persisted so it is not re-asked. */
    fun continueOffline() {
        prefs.edit().putBoolean(KEY_BYPASS, true).apply()
        _state.update { it.copy(offlineBypass = true, error = null) }
    }

    // ---- email / password ----

    fun submitEmail() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }

        viewModelScope.launch {
            val result = if (s.creatingAccount) {
                repo.createEmailAccount(s.email, s.password)
            } else {
                repo.signInWithEmail(s.email, s.password)
            }
            result
                .onSuccess { user ->
                    _state.update { it.copy(busy = false, user = user, password = "", error = null) }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(busy = false, error = message(FirebaseAuthRepository.classify(t)))
                    }
                }
        }
    }

    fun sendPasswordReset() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            repo.sendPasswordReset(s.email)
                .onSuccess {
                    _state.update {
                        it.copy(busy = false, notice = "Reset link sent to ${s.email}.")
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(busy = false, error = message(FirebaseAuthRepository.classify(t)))
                    }
                }
        }
    }

    // ---- phone / OTP ----

    fun sendOtp(activity: Activity, isResend: Boolean = false) {
        val s = _state.value
        if (s.busy) return
        if (!FirebaseAuthRepository.isPlausibleE164(s.e164)) {
            _state.update { it.copy(error = message(AuthFailure.INVALID_PHONE)) }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repo.sendOtp(activity, s.e164, if (isResend) resendToken else null)) {
                is OtpSendResult.CodeSent -> {
                    verificationId = result.verificationId
                    resendToken = result.resendToken
                    _state.update { it.copy(busy = false, step = AuthStep.CODE, error = null) }
                    startResendCountdown()
                }
                is OtpSendResult.AutoVerified ->
                    _state.update { it.copy(busy = false, user = result.user, error = null) }
                is OtpSendResult.Failed ->
                    _state.update { it.copy(busy = false, error = message(result.reason)) }
            }
        }
    }

    fun verifyOtp() {
        val s = _state.value
        val id = verificationId
        if (s.busy) return
        if (id == null) {
            _state.update { it.copy(error = "Request a new code to continue.") }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.verifyOtp(id, s.code)
                .onSuccess { user ->
                    countdownJob?.cancel()
                    _state.update { it.copy(busy = false, user = user, code = "", error = null) }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(busy = false, error = message(FirebaseAuthRepository.classify(t)))
                    }
                }
        }
    }

    fun signOut() {
        repo.signOut()
        countdownJob?.cancel()
        verificationId = null
        resendToken = null
        prefs.edit().putBoolean(KEY_BYPASS, false).apply()
        _state.value = AuthUiState(firebaseAvailable = repo.isAvailable)
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = FirebaseAuthRepository.OTP_TIMEOUT_SECONDS.toInt()
            while (remaining > 0) {
                _state.update { it.copy(resendSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(resendSeconds = 0) }
        }
    }

    private fun message(failure: AuthFailure): String = when (failure) {
        AuthFailure.NOT_CONFIGURED -> "Sign-in is not configured in this build."
        AuthFailure.INVALID_PHONE -> "Enter a valid phone number including country code."
        AuthFailure.INVALID_CODE -> "That code is not correct. Check and try again."
        AuthFailure.CODE_EXPIRED -> "That code has expired. Request a new one."
        AuthFailure.TOO_MANY_ATTEMPTS -> "Too many attempts. Try again later."
        AuthFailure.NETWORK_UNAVAILABLE ->
            "No connection. Signing in needs network - you can continue offline instead."
        AuthFailure.INVALID_EMAIL -> "Enter a valid email address."
        AuthFailure.WRONG_PASSWORD -> "Incorrect email or password."
        AuthFailure.WEAK_PASSWORD ->
            "Use at least ${FirebaseAuthRepository.MIN_PASSWORD_LENGTH} characters."
        AuthFailure.EMAIL_ALREADY_IN_USE -> "That email already has an account. Sign in instead."
        AuthFailure.NO_SUCH_USER -> "No account for that email. Create one instead."
        AuthFailure.MISSING_APP_CREDENTIAL ->
            "Phone sign-in is not authorised for this build: the app's SHA-1 " +
                "fingerprint is not registered in Firebase. Use email, or continue offline."
        AuthFailure.UNKNOWN -> "Sign-in failed. Please try again."
    }

    private companion object {
        const val PREFS = "devcraft_auth"
        const val KEY_BYPASS = "offline_bypass"
    }
}
