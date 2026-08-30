package com.devcraft.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import org.junit.Assert.*
import org.junit.Test

/**
 * The pure parts of phone auth: number validation and failure classification.
 * The Firebase calls themselves need a device and a live project.
 */
class PhoneAuthRepositoryTest {

    @Test
    fun acceptsPlausibleIndianNumbers() {
        assertTrue(PhoneAuthRepository.isPlausibleE164("+919876543210"))
        assertTrue(PhoneAuthRepository.isPlausibleE164("+14155552671"))
    }

    @Test
    fun rejectsNumbersWithoutCountryCode() {
        assertFalse(PhoneAuthRepository.isPlausibleE164("9876543210"))
        assertFalse(PhoneAuthRepository.isPlausibleE164("09876543210"))
    }

    @Test
    fun rejectsNonDigitsAndBadLengths() {
        assertFalse(PhoneAuthRepository.isPlausibleE164("+91 98765 43210"))
        assertFalse(PhoneAuthRepository.isPlausibleE164("+91-9876543210"))
        assertFalse(PhoneAuthRepository.isPlausibleE164("+123"))
        assertFalse(PhoneAuthRepository.isPlausibleE164("+1234567890123456789"))
        assertFalse(PhoneAuthRepository.isPlausibleE164(""))
        assertFalse(PhoneAuthRepository.isPlausibleE164("+"))
    }

    @Test
    fun classifiesNetworkFailureSoTheUiCanOfferOfflineInstead() {
        assertEquals(
            AuthFailure.NETWORK_UNAVAILABLE,
            PhoneAuthRepository.classify(FirebaseNetworkException("no connection")),
        )
    }

    @Test
    fun classifiesQuotaExhaustion() {
        assertEquals(
            AuthFailure.TOO_MANY_ATTEMPTS,
            PhoneAuthRepository.classify(FirebaseTooManyRequestsException("quota")),
        )
    }

    @Test
    fun preservesExplicitFailures() {
        assertEquals(
            AuthFailure.NOT_CONFIGURED,
            PhoneAuthRepository.classify(AuthException(AuthFailure.NOT_CONFIGURED)),
        )
        assertEquals(
            AuthFailure.CODE_EXPIRED,
            PhoneAuthRepository.classify(AuthException(AuthFailure.CODE_EXPIRED)),
        )
    }

    @Test
    fun unknownFailuresDoNotMasqueradeAsSomethingSpecific() {
        assertEquals(AuthFailure.UNKNOWN, PhoneAuthRepository.classify(RuntimeException("boom")))
    }
}
