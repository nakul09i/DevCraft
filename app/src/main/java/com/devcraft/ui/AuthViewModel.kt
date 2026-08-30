package com.devcraft.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcraft.auth.AuthFailure
import com.devcraft.auth.AuthUser
import com.devcraft.auth.OtpSendResult
import com.devcraft.auth.PhoneAuthRepository
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthStep { PHONE, CODE }

data class AuthUiState(
    /** False when no Firebase config is bundled; the app must then skip login entirely. */
    val firebaseAvailable: Boolean = false,
    val user: AuthUser? = null,
    val step: AuthStep = AuthStep.PHONE,
    val countryCode: String = "+91",
    val phone: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val resendSeconds: Int = 0,
    /** Merchant chose to work offline without signing in. */
    val offlineBypass: Boolean = false,
) {
    val e164: String get() = countryCode + phone.filter { it.isDigit() }

    /** The app content is reachable when auth is unavailable, done, or bypassed. */
    val canEnterApp: Boolean get() = !firebaseAvailable || user != null || offlineBypass
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PhoneAuthRepository(application)
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

    fun setCountryCode(value: String) = _state.update { it.copy(countryCode = value, error = null) }
    fun setPhone(value: String) = _state.update { it.copy(phone = value.filter { c -> c.isDigit() }, error = null) }
    fun setCode(value: String) = _state.update { it.copy(code = value.filter { c -> c.isDigit() }.take(6), error = null) }

    /** Explicit "use offline without signing in". Persisted so it is not re-asked. */
    fun continueOffline() {
        prefs.edit().putBoolean(KEY_BYPASS, true).apply()
        _state.update { it.copy(offlineBypass = true, error = null) }
    }

    fun backToPhone() {
        countdownJob?.cancel()
        _state.update { it.copy(step = AuthStep.PHONE, code = "", error = null, resendSeconds = 0) }
    }

    fun sendOtp(activity: Activity, isResend: Boolean = false) {
        val current = _state.value
        if (current.busy) return
        if (!PhoneAuthRepository.isPlausibleE164(current.e164)) {
            _state.update { it.copy(error = message(AuthFailure.INVALID_PHONE)) }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repo.sendOtp(activity, current.e164, if (isResend) resendToken else null)) {
                is OtpSendResult.CodeSent -> {
                    verificationId = result.verificationId
                    resendToken = result.resendToken
                    _state.update { it.copy(busy = false, step = AuthStep.CODE, error = null) }
                    startResendCountdown()
                }
                is OtpSendResult.AutoVerified -> {
                    _state.update { it.copy(busy = false, user = result.user, error = null) }
                }
                is OtpSendResult.Failed -> {
                    _state.update { it.copy(busy = false, error = message(result.reason)) }
                }
            }
        }
    }

    fun verifyOtp() {
        val current = _state.value
        val id = verificationId
        if (current.busy) return
        if (id == null) {
            _state.update { it.copy(error = "Request a new code to continue.") }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.verifyOtp(id, current.code)
                .onSuccess { user ->
                    countdownJob?.cancel()
                    _state.update { it.copy(busy = false, user = user, code = "", error = null) }
                }
                .onFailure { t ->
                    _state.update { it.copy(busy = false, error = message(PhoneAuthRepository.classify(t))) }
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
            var remaining = PhoneAuthRepository.OTP_TIMEOUT_SECONDS.toInt()
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
        AuthFailure.NETWORK_UNAVAILABLE -> "No connection. Sending a code needs network - you can continue offline instead."
        AuthFailure.UNKNOWN -> "Sign-in failed. Please try again."
    }

    private companion object {
        const val PREFS = "devcraft_auth"
        const val KEY_BYPASS = "offline_bypass"
    }
}
