package com.devcraft.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Local due-date alerts. AlarmManager only - no external notification service,
 * no network. Owns the "due date string -> trigger time" rule so the ViewModel
 * and the boot receiver cannot disagree about it.
 */
class LocalAlertScheduler(private val context: Context) {

    /**
     * Schedules the 9am reminder for [dueDate] (yyyy-MM-dd).
     * Returns false if the date is unparseable or already in the past.
     */
    fun scheduleForDueDate(orderId: String, customerName: String, dueDate: String?): Boolean {
        val triggerAt = triggerTimeFor(dueDate) ?: return false
        if (triggerAt <= System.currentTimeMillis()) return false
        return schedule(orderId, customerName, triggerAt)
    }

    fun schedule(orderId: String, customerName: String, triggerAtMillis: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        val pendingIntent = alarmPendingIntent(orderId, customerName)

        return try {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } else {
                // The user revoked exact-alarm permission (API 31+). Degrade to an
                // inexact alarm rather than dropping the reminder entirely.
                Log.i(TAG, "Exact alarms unavailable; scheduling inexact for $orderId")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarm scheduling denied for $orderId", e)
            false
        }
    }

    fun cancel(orderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(alarmPendingIntent(orderId, ""))
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(orderId: String, customerName: String): PendingIntent {
        val intent = Intent(context, OrderDueReceiver::class.java).apply {
            putExtra(EXTRA_ORDER_ID, orderId)
            putExtra(EXTRA_CUSTOMER_NAME, customerName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(orderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "LocalAlertScheduler"
        const val EXTRA_ORDER_ID = "com.devcraft.extra.ORDER_ID"
        const val EXTRA_CUSTOMER_NAME = "com.devcraft.extra.CUSTOMER_NAME"

        /** Reminders fire at 9am local time on the due date. */
        const val ALERT_HOUR = 9

        fun requestCodeFor(orderId: String): Int = orderId.hashCode()

        fun triggerTimeFor(dueDate: String?): Long? {
            if (dueDate.isNullOrBlank()) return null
            val parsed = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(dueDate)
            } catch (e: Exception) {
                null
            } ?: return null

            return Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, ALERT_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}
