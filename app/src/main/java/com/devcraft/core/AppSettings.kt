package com.devcraft.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted user preferences.
 *
 * Read synchronously from SharedPreferences because [smsCaptureEnabled] is
 * checked inside a BroadcastReceiver, where there is no scope to collect a Flow
 * before deciding whether to handle the broadcast.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _smsCaptureEnabled = MutableStateFlow(prefs.getBoolean(KEY_SMS_CAPTURE, false))
    val smsCaptureEnabled: StateFlow<Boolean> = _smsCaptureEnabled.asStateFlow()

    private val _notificationCaptureEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_CAPTURE, false))
    val notificationCaptureEnabled: StateFlow<Boolean> = _notificationCaptureEnabled.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(readLastSync())
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

    private fun readLastSync(): Long? =
        prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    companion object {
        private const val PREFS = "devcraft_settings"
        private const val KEY_SMS_CAPTURE = "sms_capture_enabled"
        private const val KEY_NOTIFICATION_CAPTURE = "notification_capture_enabled"
        private const val KEY_LAST_SYNC = "last_sync_at"

        /**
         * Synchronous read for BroadcastReceivers. Defaults to false, so SMS is
         * never processed unless the merchant explicitly turned it on.
         */
        fun isSmsCaptureEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SMS_CAPTURE, false)

        /** Synchronous read for NotificationListenerService. Default OFF. */
        fun isNotificationCaptureEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATION_CAPTURE, false)
    }
}
