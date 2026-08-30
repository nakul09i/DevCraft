package com.devcraft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.devcraft.data.local.database.DevCraftDatabase
import java.util.UUID

class DevCraftApplication : Application() {
    val database: DevCraftDatabase by lazy { DevCraftDatabase.getDatabase(this) }

    /**
     * Stable per-install identity. Must survive process death: the operation log
     * attributes every mutation to it, and it is the deterministic tie-breaker
     * when two devices edit the same field at the same logical time. It was
     * previously regenerated per ViewModel, so every launch looked like a new
     * device and the tie-breaker was meaningless.
     */
    val deviceId: String by lazy {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Order due alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders for orders due today or upcoming" }
        )

        // Separate channel so captured-message pings can be silenced without
        // also silencing due-date reminders.
        manager.createNotificationChannel(
            NotificationChannel(
                CAPTURE_CHANNEL_ID,
                "Captured messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A new SMS or shared message was captured and interpreted"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "devcraft_order_due_channel"
        const val CAPTURE_CHANNEL_ID = "devcraft_captured_message_channel"
        private const val PREFS = "devcraft_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
