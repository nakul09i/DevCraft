package com.devcraft.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.devcraft.DevCraftApplication
import com.devcraft.core.AppSettings
import com.devcraft.data.ingest.MessageIngestor
import com.devcraft.data.local.database.DevCraftDatabase
import com.devcraft.data.local.entities.MessageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Channel B: incoming customer SMS as order messages.
 *
 * Technically this works without being the default SMS app - SMS_RECEIVED is a
 * broadcast any app holding RECEIVE_SMS can observe. Only SMS_DELIVER, writing
 * to the SMS provider, and sending-as-default require default-handler status,
 * and DevCraft does none of those.
 *
 * The permission is requested on demand from the dashboard, never at startup,
 * because RECEIVE_SMS is a Play Store restricted permission - see docs.
 *
 * Deliberately separate from Firebase phone OTP: that is authentication, this is
 * order ingestion. [looksLikeVerificationCode] keeps the two from crossing, so
 * DevCraft's own login SMS never lands in the order inbox.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Setting is OFF by default; when off we do not even look at the message.
        if (!AppSettings.isSmsCaptureEnabled(context)) {
            Log.i(TAG, "SMS order capture is off; ignoring broadcast")
            return
        }

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?.filterNotNull()
            ?: return
        if (parts.isEmpty()) return

        // Multipart SMS arrives as several PDUs; reassemble into one message.
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }
        val sender = parts.firstOrNull()?.displayOriginatingAddress

        if (body.isBlank()) return
        if (looksLikeVerificationCode(body)) {
            Log.i(TAG, "Ignoring an authentication/OTP style SMS")
            return
        }

        val appContext = context.applicationContext
        val deviceId = (appContext as? DevCraftApplication)?.deviceId ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ingestor = MessageIngestor(DevCraftDatabase.getDatabase(appContext), deviceId)
                val id = ingestor.ingest(
                    text = body,
                    source = MessageSource.SMS.name,
                    sender = sender,
                    senderName = null,
                )
                Log.i(TAG, if (id != null) "Ingested SMS as message $id" else "SMS body was blank")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"

        private val AUTH_MARKERS = listOf(
            "otp", "one time password", "one-time password", "verification code",
            "verify code", "verification pin", "do not share", "never share",
            "security code", "login code", "auth code", "2fa",
        )

        /**
         * True for authentication SMS, which must never be treated as an order.
         * Catches Firebase's own phone-auth message ("... is your verification
         * code for <project>.firebaseapp.com") and bare numeric code texts.
         */
        fun looksLikeVerificationCode(body: String): Boolean {
            val lower = body.lowercase()
            if (AUTH_MARKERS.any { lower.contains(it) }) return true

            // A short, purely numeric body is a code, not an order.
            val trimmed = body.trim()
            return trimmed.length <= 8 && trimmed.isNotEmpty() && trimmed.all { it.isDigit() }
        }
    }
}
