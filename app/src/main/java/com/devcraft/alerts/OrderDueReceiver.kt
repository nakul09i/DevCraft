package com.devcraft.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.devcraft.DevCraftApplication

class OrderDueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getStringExtra("ORDER_ID") ?: return
        val customerName = intent.getStringExtra("CUSTOMER_NAME") ?: "Customer"

        val builder = NotificationCompat.Builder(context, DevCraftApplication.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DevCraft: Order Due Alert!")
            .setContentText("Order for $customerName is due today.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(orderId.hashCode(), builder.build())
        }
    }
}
