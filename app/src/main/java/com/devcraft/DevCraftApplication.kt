package com.devcraft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.devcraft.data.local.database.DevCraftDatabase

class DevCraftApplication : Application() {
    val database: DevCraftDatabase by lazy { DevCraftDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DevCraft Order Due Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for orders due today or upcoming"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "devcraft_order_due_channel"
    }
}
