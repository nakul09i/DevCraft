package com.devcraft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.entities.MessageSource

/**
 * Shared visual vocabulary. Every status in DevCraft is an icon plus a word, not
 * a colour alone: colour-only encoding is unreadable for colour-blind users and
 * ambiguous in bright sunlight, which is where a merchant actually uses this.
 */

// ---------------------------------------------------------------- source

/** Where a message came from, as icon + label. */
@Composable
fun SourceBadge(source: String?, modifier: Modifier = Modifier) {
    val (icon, label, tint) = sourceVisual(source)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

private fun sourceVisual(source: String?): Triple<ImageVector, String, Color> = when (source) {
    MessageSource.WHATSAPP_SHARE.name ->
        Triple(Icons.Default.Chat, "WhatsApp", Color(0xFF128C7E))
    MessageSource.SMS.name ->
        Triple(Icons.Default.Sms, "SMS", Color(0xFF1565C0))
    MessageSource.NOTIFICATION.name ->
        Triple(Icons.Default.NotificationsActive, "Notification", Color(0xFF6A1B9A))
    MessageSource.MANUAL.name ->
        Triple(Icons.Default.EditNote, "Manual", Color(0xFF455A64))
    MessageSource.OTHER_SHARE.name ->
        Triple(Icons.Default.Share, "Shared", Color(0xFF00695C))
    else ->
        Triple(Icons.Default.HelpOutline, MessageSource.labelOf(source), Color(0xFF616161))
}

// ---------------------------------------------------------------- message status

/** Where a message is in the review pipeline. */
@Composable
fun MessageStatusChip(status: String, modifier: Modifier = Modifier) {
    val (icon, label, tint) = when (status) {
        "CONVERTED" -> Triple(Icons.Default.CheckCircle, "Converted", Color(0xFF2E7D32))
        "PARSED" -> Triple(Icons.Default.AutoFixHigh, "Parsed", Color(0xFF1565C0))
        "REVIEWED" -> Triple(Icons.Default.RateReview, "Reviewed", Color(0xFFE65100))
        "ERROR" -> Triple(Icons.Default.ErrorOutline, "Error", Color(0xFFC62828))
        "ARCHIVED" -> Triple(Icons.Default.Archive, "Archived", Color(0xFF616161))
        else -> Triple(Icons.Default.Inbox, "Received", Color(0xFF616161))
    }
    Surface(shape = RoundedCornerShape(6.dp), color = tint.copy(alpha = 0.14f), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint)
        }
    }
}

// ---------------------------------------------------------------- order status

/** Order lifecycle state. */
@Composable
fun OrderStatusPill(status: String, modifier: Modifier = Modifier) {
    val (icon, tint) = when (status) {
        "COMPLETED" -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
        "PROCESSING" -> Icons.Default.LocalShipping to Color(0xFF1565C0)
        "CANCELLED" -> Icons.Default.Cancel to Color(0xFFC62828)
        "CONFIRMED" -> Icons.Default.TaskAlt to Color(0xFF00695C)
        else -> Icons.Default.PendingActions to Color(0xFFE65100)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = tint.copy(alpha = 0.14f), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

// ---------------------------------------------------------------- confidence

/**
 * How sure the parser is, as icon + bar + number. Three redundant encodings
 * because this is the one signal that decides whether a merchant should look
 * closely before confirming.
 */
@Composable
fun ConfidenceMeter(confidence: Float, needsReview: Boolean, modifier: Modifier = Modifier) {
    val high = confidence >= 0.8f && !needsReview
    val tint = when {
        high -> Color(0xFF2E7D32)
        confidence >= 0.7f -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }
    val icon = when {
        high -> Icons.Default.Verified
        confidence >= 0.7f -> Icons.Default.WarningAmber
        else -> Icons.Default.ReportProblem
    }
    val label = when {
        high -> "High confidence"
        confidence >= 0.7f -> "Check before confirming"
        else -> "Needs review"
    }

    Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.10f), modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                color = tint,
                trackColor = tint.copy(alpha = 0.2f),
                modifier = Modifier
                    .width(56.dp)
                    .height(5.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${(confidence * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

// ---------------------------------------------------------------- table row

/** Icon + label + value. The icon is what makes a dense table scannable. */
@Composable
fun IconLabelRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val missing = value.isBlank() || value == "—"
    val contentColor = if (missing) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (missing) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            if (missing) "Not found" else value,
            fontSize = 13.sp,
            fontWeight = if (missing) FontWeight.Normal else FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** Icons used by the interpretation table, kept in one place. */
object FieldIcons {
    val Customer = Icons.Default.Person
    val Phone = Icons.Default.Phone
    val Item = Icons.Default.Inventory2
    val Quantity = Icons.Default.Numbers
    val Amount = Icons.Default.CurrencyRupee
    val DueDate = Icons.Default.Event
    val Address = Icons.Default.LocationOn
    val Pincode = Icons.Default.MyLocation
    val Repeat = Icons.Default.Repeat
    val Colour = Icons.Default.Palette
    val Size = Icons.Default.Straighten
    val Attribute = Icons.Default.Label
}
