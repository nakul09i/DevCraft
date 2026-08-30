package com.devcraft.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.devcraft.data.local.database.DevCraftDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android drops every AlarmManager alarm on reboot, so without this every
 * pending due-date reminder was silently lost the first time the phone
 * restarted. Replays them from Room, which is the source of truth.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = LocalAlertScheduler(appContext)
                val orders = DevCraftDatabase.getDatabase(appContext)
                    .orderDao()
                    .getOrdersAwaitingDueDate()

                var rescheduled = 0
                for (order in orders) {
                    val name = order.customerName ?: "Customer"
                    if (scheduler.scheduleForDueDate(order.orderId, name, order.dueDate)) {
                        rescheduled++
                    }
                }
                Log.i(TAG, "Rescheduled $rescheduled of ${orders.size} due-date alerts after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alerts after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
