package com.devcraft.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure parts of authentication: input validation and failure classification.
 * The Firebase calls themselves need a device and a live project.
 */
class FirebaseAuthRepositoryTest {

    // ---- phone validation ----

    @Test
    fun acceptsPlausibleE164Numbers() {
        assertTrue(FirebaseAuthRepository.isPlausibleE164("+919876543210"))
        assertTrue(FirebaseAuthRepository.isPlausibleE164("+14155552671"))
    }

    @Test
    fun rejectsPhoneWithoutCountryCodeOrWithSeparators() {
        assertFalse(FirebaseAuthRepository.isPlausibleE164("9876543210"))
        assertFalse(FirebaseAuthRepository.isPlausibleE164("+91 98765 43210"))
        assertFalse(FirebaseAuthRepository.isPlausibleE164("+91-9876543210"))
        assertFalse(FirebaseAuthRepository.isPlausibleE164("+123"))
        assertFalse(FirebaseAuthRepository.isPlausibleE164(""))
        assertFalse(FirebaseAuthRepository.isPlausibleE164("+"))
    }

    // ---- email validation ----

    @Test
    fun acceptsOrdinaryEmails() {
        assertTrue(FirebaseAuthRepository.isPlausibleEmail("nakul@example.com"))
        assertTrue(FirebaseAuthRepository.isPlausibleEmail("a.b+tag@sub.domain.co.in"))
        assertTrue(FirebaseAuthRepository.isPlausibleEmail("  spaced@example.com  "))
    }

    @Test
    fun rejectsObviousEmailTypos() {
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("no-at-sign.com"))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("two@@example.com"))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("@example.com"))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("user@nodot"))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("user@.com"))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("user@example."))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail("has space@example.com"))
        assertFalse(FirebaseAuthRepository.isPlausibleEmail(""))
    }

    // ---- failure classification ----

    @Test
    fun networkFailureIsDistinctSoTheUiCanOfferOffline() {
        assertEquals(
            AuthFailure.NETWORK_UNAVAILABLE,
            FirebaseAuthRepository.classify(FirebaseNetworkException("no connection")),
        )
    }

    @Test
    fun quotaExhaustionIsDistinct() {
        assertEquals(
            AuthFailure.TOO_MANY_ATTEMPTS,
            FirebaseAuthRepository.classify(FirebaseTooManyRequestsException("quota")),
        )
    }

    @Test
    fun missingShaFingerprintIsIdentifiedByName() {
        // This is the actual failure when the app's SHA-1 is not registered.
        assertEquals(
            AuthFailure.MISSING_APP_CREDENTIAL,
            FirebaseAuthRepository.classify(RuntimeException("INVALID_APP_CREDENTIAL")),
        )
        assertEquals(
            AuthFailure.MISSING_APP_CREDENTIAL,
            FirebaseAuthRepository.classify(RuntimeException("APP_NOT_AUTHORIZED for this project")),
        )
    }

    @Test
    fun explicitFailuresArePreserved() {
        assertEquals(
            AuthFailure.NOT_CONFIGURED,
            FirebaseAuthRepository.classify(AuthException(AuthFailure.NOT_CONFIGURED)),
        )
        assertEquals(
            AuthFailure.CODE_EXPIRED,
            FirebaseAuthRepository.classify(AuthException(AuthFailure.CODE_EXPIRED)),
        )
    }

    @Test
    fun unknownFailuresDoNotMasqueradeAsSomethingSpecific() {
        assertEquals(AuthFailure.UNKNOWN, FirebaseAuthRepository.classify(RuntimeException("boom")))
    }

    @Test
    fun minimumPasswordLengthMatchesFirebaseRequirement() {
        assertEquals(6, FirebaseAuthRepository.MIN_PASSWORD_LENGTH)
    }

    @Test
    fun authUserPrefersPhoneThenEmailForDisplay() {
        assertEquals("+919876543210", AuthUser("uid1", "+919876543210", "a@b.com").displayIdentity)
        assertEquals("a@b.com", AuthUser("uid1", null, "a@b.com").displayIdentity)
        assertEquals("abcdefgh", AuthUser("abcdefgh-rest", null, null).displayIdentity)
    }
}
