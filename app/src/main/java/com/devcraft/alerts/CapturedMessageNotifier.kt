package com.devcraft.alerts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.devcraft.DevCraftApplication
import com.devcraft.MainActivity
import com.devcraft.R

/**
 * Tells the merchant a message was captured and already interpreted while the
 * app was closed. Without this, background SMS capture is invisible until the
 * app is opened by chance.
 *
 * Tapping it opens the review screen for that message. Nothing is auto-confirmed
 * - the order is only created after the merchant reviews.
 */
object CapturedMessageNotifier {

    const val EXTRA_MESSAGE_ID = "com.devcraft.extra.MESSAGE_ID"

    fun notifyCaptured(
        context: Context,
        messageId: String,
        source: String,
        preview: String,
        confidence: Float,
        needsReview: Boolean,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val deepLink = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            messageId.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val percent = (confidence * 100).toInt()
        val title = if (needsReview) {
            "New $source order needs review"
        } else {
            "New $source order captured ($percent%)"
        }

        val notification = NotificationCompat.Builder(context, DevCraftApplication.CAPTURE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_d)
            .setContentTitle(title)
            .setContentText(preview.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview.take(400)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(messageId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }
}
