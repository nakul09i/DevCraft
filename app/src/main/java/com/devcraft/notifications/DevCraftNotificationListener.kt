package com.devcraft.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.devcraft.DevCraftApplication
import com.devcraft.alerts.CapturedMessageNotifier
import com.devcraft.core.AppSettings
import com.devcraft.data.ingest.MessageIngestor
import com.devcraft.data.local.database.DevCraftDatabase
import com.devcraft.data.local.entities.MessageSource
import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.sms.SmsReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads incoming messaging notifications so an order can be captured without the
 * user manually sharing it.
 *
 * This is NOT WhatsApp database scraping, which DevCraft does not and will not
 * do. This is Android's documented NotificationListenerService: it only ever
 * sees text the system has already rendered on screen, and it requires the user
 * to grant "Notification access" explicitly in system settings - a permission
 * Android never grants silently and shows in its own settings list.
 *
 * Constraints that are real and worth knowing:
 *  - notification text is what the sender app chose to display. It is often
 *    truncated, and group summaries say things like "3 new messages" with no
 *    body at all. Those are skipped rather than stored as garbage.
 *  - only an allow-list of messaging apps is read. Banking, email and personal
 *    notifications are ignored outright and never persisted.
 *  - Play Store treats notification access as sensitive. See docs.
 */
class DevCraftNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        // Android has actually bound us. Surfaced in Settings so a silent
        // never-connected state is visible instead of looking like "idle".
        AppSettings.setListenerConnected(applicationContext, true)
    }

    override fun onListenerDisconnected() {
        AppSettings.setListenerConnected(applicationContext, false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val context = applicationContext

        if (!AppSettings.isNotificationCaptureEnabled(context)) {
            AppSettings.recordSkip(context, "Notification capture is switched off")
            return
        }

        // Never read our own notifications back in.
        if (sbn.packageName == context.packageName) return

        AppSettings.recordSeen(context, AppSettings.Channel.NOTIFICATION)

        if (sbn.packageName !in SUPPORTED_PACKAGES) {
            AppSettings.recordSkip(context, "Ignored ${sbn.packageName} (not a messaging app)")
            return
        }

        val extras = sbn.notification?.extras ?: return

        // Group summaries carry no usable body ("3 new messages"). Skip them.
        val isGroupSummary =
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            AppSettings.recordSkip(context, "Skipped a grouped summary notification")
            return
        }

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val body = extractBody(extras)

        if (body.isNullOrBlank()) {
            AppSettings.recordSkip(context, "Notification had no readable text")
            return
        }
        if (isNonMessageNoise(body, sender)) {
            AppSettings.recordSkip(context, "Skipped chat noise: \"${body.take(40)}\"")
            return
        }
        // Authentication codes must never become orders.
        if (SmsReceiver.looksLikeVerificationCode(body)) {
            AppSettings.recordSkip(context, "Skipped a verification code")
            return
        }

        val deviceId = (context as? DevCraftApplication)?.deviceId ?: return
        val appLabel = SUPPORTED_PACKAGES[sbn.packageName] ?: "Notification"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ingestor = MessageIngestor(DevCraftDatabase.getDatabase(context), deviceId)
                val id = ingestor.ingest(
                    text = body,
                    source = MessageSource.NOTIFICATION.name,
                    sender = sbn.packageName,
                    senderName = sender,
                )
                if (id != null) {
                    AppSettings.recordCapture(
                        context, AppSettings.Channel.NOTIFICATION, System.currentTimeMillis()
                    )
                    AppSettings.recordSkip(context, "Captured from $appLabel")
                    val parsed = DeterministicParser.parse(body)
                    CapturedMessageNotifier.notifyCaptured(
                        context = context,
                        messageId = id,
                        source = appLabel,
                        preview = body,
                        confidence = parsed.confidence,
                        needsReview = parsed.needs_clarification,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest notification from ${sbn.packageName}", e)
            }
        }
    }

    companion object {
        private const val TAG = "DevCraftNotifListener"

        /**
         * Messaging apps only, mapped to a display label. Everything else on the
         * device is ignored - DevCraft has no business reading a bank alert.
         */
        val SUPPORTED_PACKAGES: Map<String, String> = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "org.thoughtcrime.securesms" to "Signal",
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Messages",
        )

        /** Chat-app chrome that is not an order. */
        private val NOISE_MARKERS = listOf(
            "new messages", "new message from", "missed call", "incoming call",
            "is typing", "you were mentioned", "checking for new messages",
            "backup in progress", "tap to view", "photo", "video", "voice message",
            "sticker", "gif", "document", "deleted this message",
        )

        /**
         * WhatsApp and Telegram often post MessagingStyle notifications where the
         * body lives in EXTRA_TEXT_LINES rather than EXTRA_TEXT. Reading only
         * EXTRA_TEXT missed those entirely, which is a common reason capture
         * appears to do nothing. Takes the newest line.
         */
        fun extractBody(extras: android.os.Bundle): String? {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim() }
                ?.lastOrNull { it.isNotBlank() }
                ?.let { return it }

            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }?.let { return it }

            return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }
        }

        fun isNonMessageNoise(body: String, sender: String?): Boolean {
            val lower = body.trim().lowercase()
            if (lower.length < 8) return true
            if (NOISE_MARKERS.any { lower == it || lower.startsWith(it) }) return true
            // "3 new messages" style summaries
            if (Regex("^\\d+\\s+new\\s+messages?$").matches(lower)) return true
            return false
        }

        /** True when the user has granted notification access to DevCraft. */
        fun isAccessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        /**
         * Opens the system screen where notification access is granted. There is
         * no runtime-permission dialog for this; the user must toggle it there.
         */
        fun accessSettingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
