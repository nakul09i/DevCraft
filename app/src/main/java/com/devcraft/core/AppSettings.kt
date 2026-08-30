package com.devcraft.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted user preferences plus capture diagnostics.
 *
 * Read synchronously from SharedPreferences because the capture flags are
 * checked inside a BroadcastReceiver and a NotificationListenerService, where
 * there is no scope to collect a Flow before deciding whether to handle an event.
 *
 * The diagnostics exist because a channel that silently captures nothing is
 * otherwise indistinguishable from one that is working but idle - which is
 * exactly the situation that makes this feature feel broken.
 */
class AppSettings(context: Context) {

    private val prefs = prefs(context)

    private val _smsCaptureEnabled = MutableStateFlow(prefs.getBoolean(KEY_SMS_CAPTURE, false))
    val smsCaptureEnabled: StateFlow<Boolean> = _smsCaptureEnabled.asStateFlow()

    private val _notificationCaptureEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_CAPTURE, false))
    val notificationCaptureEnabled: StateFlow<Boolean> = _notificationCaptureEnabled.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.nullableLong(KEY_LAST_SYNC))
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt.asStateFlow()

    fun setSmsCaptureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_CAPTURE, enabled).apply()
        _smsCaptureEnabled.value = enabled
    }

    fun setNotificationCaptureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_CAPTURE, enabled).apply()
        _notificationCaptureEnabled.value = enabled
    }

    fun recordSuccessfulSync(atMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, atMillis).apply()
        _lastSyncAt.value = atMillis
    }

    /** Re-read the values receivers/services wrote from another entry point. */
    fun diagnostics(): CaptureDiagnostics = CaptureDiagnostics(
        lastSmsCaptureAt = prefs.nullableLong(KEY_LAST_SMS),
        lastNotificationCaptureAt = prefs.nullableLong(KEY_LAST_NOTIF),
        lastSkipReason = prefs.getString(KEY_LAST_SKIP, null),
        listenerConnected = prefs.getBoolean(KEY_LISTENER_CONNECTED, false),
        smsSeenCount = prefs.getInt(KEY_SMS_SEEN, 0),
        notificationSeenCount = prefs.getInt(KEY_NOTIF_SEEN, 0),
    )

    companion object {
        private const val PREFS = "devcraft_settings"
        private const val KEY_SMS_CAPTURE = "sms_capture_enabled"
        private const val KEY_NOTIFICATION_CAPTURE = "notification_capture_enabled"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_LAST_SMS = "last_sms_capture_at"
        private const val KEY_LAST_NOTIF = "last_notification_capture_at"
        private const val KEY_LAST_SKIP = "last_skip_reason"
        private const val KEY_LISTENER_CONNECTED = "listener_connected"
        private const val KEY_SMS_SEEN = "sms_seen_count"
        private const val KEY_NOTIF_SEEN = "notif_seen_count"

        fun isSmsCaptureEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_SMS_CAPTURE, false)

        fun isNotificationCaptureEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_NOTIFICATION_CAPTURE, false)

        /** A message was stored. */
        fun recordCapture(context: Context, channel: Channel, atMillis: Long) {
            val key = if (channel == Channel.SMS) KEY_LAST_SMS else KEY_LAST_NOTIF
            prefs(context).edit().putLong(key, atMillis).apply()
        }

        /**
         * An event arrived at the channel, whether or not it was stored. This is
         * what distinguishes "nothing is reaching us" from "things reach us but
         * are being filtered out".
         */
        fun recordSeen(context: Context, channel: Channel) {
            val key = if (channel == Channel.SMS) KEY_SMS_SEEN else KEY_NOTIF_SEEN
            val p = prefs(context)
            p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
        }

        fun recordSkip(context: Context, reason: String) {
            prefs(context).edit().putString(KEY_LAST_SKIP, reason).apply()
        }

        fun setListenerConnected(context: Context, connected: Boolean) {
            prefs(context).edit().putBoolean(KEY_LISTENER_CONNECTED, connected).apply()
        }

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun SharedPreferences.nullableLong(key: String): Long? =
            getLong(key, 0L).takeIf { it > 0L }
    }

    enum class Channel { SMS, NOTIFICATION }
}

data class CaptureDiagnostics(
    val lastSmsCaptureAt: Long?,
    val lastNotificationCaptureAt: Long?,
    val lastSkipReason: String?,
    /** True once Android has actually bound our NotificationListenerService. */
    val listenerConnected: Boolean,
    val smsSeenCount: Int,
    val notificationSeenCount: Int,
)
