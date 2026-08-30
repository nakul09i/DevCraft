package com.devcraft.sms

import org.junit.Assert.*
import org.junit.Test

/**
 * Keeps Channel B (customer order SMS) from swallowing authentication SMS.
 * The brief is explicit that Firebase OTP and order ingestion must not mix, and
 * DevCraft's own login code arrives by SMS on the same device.
 */
class SmsReceiverTest {

    @Test
    fun firebaseOwnVerificationSmsIsIgnored() {
        assertTrue(
            SmsReceiver.looksLikeVerificationCode(
                "123456 is your verification code for devcraft-by-neutron.firebaseapp.com"
            )
        )
    }

    @Test
    fun commonIndianOtpFormatsAreIgnored() {
        val samples = listOf(
            "Your OTP is 445566. Do not share it with anyone.",
            "998877 is your one time password. Never share this OTP.",
            "Use login code 321654 to sign in.",
            "Your security code: 778899",
            "2FA code 112233",
        )
        for (s in samples) {
            assertTrue("should have been filtered: $s", SmsReceiver.looksLikeVerificationCode(s))
        }
    }

    @Test
    fun bareNumericCodeIsIgnored() {
        assertTrue(SmsReceiver.looksLikeVerificationCode("445566"))
        assertTrue(SmsReceiver.looksLikeVerificationCode("  8821  "))
    }

    @Test
    fun realOrderMessagesAreNotFiltered() {
        val orders = listOf(
            "Ramesh bhaiya ko kal shaam 10 bori cement bhejo Rs 3500",
            "bhaiya 2 kurta chahiye navy blue chest 40 parso tak",
            "सुरेश भाई को ३ पैकेट नमकीन आज चाहिए ₹450",
            "Need 5 chairs delivered tomorrow, Rs 2500 total",
            "wahi purana order kal bhej dena please",
        )
        for (o in orders) {
            assertFalse("order was wrongly filtered: $o", SmsReceiver.looksLikeVerificationCode(o))
        }
    }

    @Test
    fun longNumericOrderQuantitiesAreNotMistakenForCodes() {
        // 9 digits or more is not a code shape
        assertFalse(SmsReceiver.looksLikeVerificationCode("123456789"))
    }

    @Test
    fun blankIsNotTreatedAsACode() {
        assertFalse(SmsReceiver.looksLikeVerificationCode(""))
        assertFalse(SmsReceiver.looksLikeVerificationCode("   "))
    }

    @Test
    fun filterIsCaseInsensitive() {
        assertTrue(SmsReceiver.looksLikeVerificationCode("YOUR OTP IS 4455"))
        assertTrue(SmsReceiver.looksLikeVerificationCode("Verification Code: 9090"))
    }
}
