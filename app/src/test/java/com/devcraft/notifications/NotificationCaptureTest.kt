package com.devcraft.notifications

import com.devcraft.data.local.entities.MessageSource
import org.junit.Assert.*
import org.junit.Test

/**
 * Filtering rules for notification capture. The service itself needs a device,
 * but these decide what is even considered a message, and they are what stop
 * DevCraft hoarding unrelated notifications.
 */
class NotificationCaptureTest {

    @Test
    fun onlyMessagingAppsAreListed() {
        val pkgs = DevCraftNotificationListener.SUPPORTED_PACKAGES.keys
        assertTrue(pkgs.contains("com.whatsapp"))
        assertTrue(pkgs.contains("com.whatsapp.w4b"))
        assertTrue(pkgs.contains("org.telegram.messenger"))
        assertTrue(pkgs.contains("com.google.android.apps.messaging"))
    }

    @Test
    fun bankingAndEmailAppsAreNotListed() {
        val pkgs = DevCraftNotificationListener.SUPPORTED_PACKAGES.keys
        // A one-line allow-list is the whole privacy story; assert it stays small.
        assertFalse(pkgs.contains("com.google.android.gm"))
        assertFalse(pkgs.contains("net.one97.paytm"))
        assertFalse(pkgs.contains("com.phonepe.app"))
        assertFalse(pkgs.contains("com.android.chrome"))
        assertTrue("allow-list should stay tight", pkgs.size <= 8)
    }

    @Test
    fun groupSummariesAndChromeAreTreatedAsNoise() {
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("3 new messages", null))
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("12 new messages", null))
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("Missed call", null))
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("Photo", null))
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("Voice message", null))
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("Checking for new messages", null))
    }

    @Test
    fun veryShortBodiesAreNoise() {
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("ok", null))
        assertTrue(DevCraftNotificationListener.isNonMessageNoise("hi", null))
    }

    @Test
    fun realOrderNotificationsSurviveTheNoiseFilter() {
        val orders = listOf(
            "Ramesh bhaiya ko kal shaam 10 bori cement bhejo Rs 3500",
            "bhaiya 2 kurta chahiye navy blue chest 40 parso tak",
            "सुरेश भाई को ३ पैकेट नमकीन आज चाहिए ₹450",
            "Rahul bhai 10 shirts blue XL 450 each Friday",
        )
        for (o in orders) {
            assertFalse("wrongly filtered: $o", DevCraftNotificationListener.isNonMessageNoise(o, null))
        }
    }

    @Test
    fun notificationSourceHasItsOwnLabel() {
        assertEquals("Notification", MessageSource.NOTIFICATION.label)
        assertEquals("WhatsApp", MessageSource.WHATSAPP_SHARE.label)
        assertEquals("Shared", MessageSource.OTHER_SHARE.label)
        assertEquals("SMS", MessageSource.SMS.label)
        assertEquals("Manual", MessageSource.MANUAL.label)
    }

    @Test
    fun unknownSourceStringFallsBackToItself() {
        assertEquals("Unknown", MessageSource.labelOf(null))
        assertEquals("SOMETHING_NEW", MessageSource.labelOf("SOMETHING_NEW"))
    }

    @Test
    fun otherShareExistsSoWeDoNotMislabelEveryShareAsWhatsApp() {
        // The honesty fix: a Telegram share is not a WhatsApp share.
        assertNotNull(MessageSource.entries.firstOrNull { it == MessageSource.OTHER_SHARE })
    }
}
