package com.devcraft.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.devcraft.DevCraftApplication
import com.devcraft.MainActivity
import com.devcraft.R

class OrderDueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getStringExtra(LocalAlertScheduler.EXTRA_ORDER_ID) ?: return
        val customerName = intent.getStringExtra(LocalAlertScheduler.EXTRA_CUSTOMER_NAME)
            ?.takeIf { it.isNotBlank() } ?: "Customer"

        val manager = NotificationManagerCompat.from(context)
        // On API 33+ POST_NOTIFICATIONS is runtime-granted; if the merchant
        // declined, posting is a silent no-op, so skip the work.
        if (!manager.areNotificationsEnabled()) return

        // Tapping the notification opens this order.
        val deepLink = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(LocalAlertScheduler.EXTRA_ORDER_ID, orderId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            LocalAlertScheduler.requestCodeFor(orderId),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, DevCraftApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_d)
            .setContentTitle("Order due today")
            .setContentText("$customerName's order is due today. Tap to open.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(LocalAlertScheduler.requestCodeFor(orderId), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }
}
