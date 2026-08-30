package com.devcraft.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.entities.MessageEntity
import com.devcraft.data.local.entities.MessageSource
import com.devcraft.ui.components.MessageStatusChip
import com.devcraft.ui.components.SourceBadge
import com.devcraft.ui.theme.DevCraftMark
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageInboxScreen(
    messages: List<MessageEntity>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onNavigateDetail: (String) -> Unit,
    onNavigateNewOrder: () -> Unit,
    onDeleteMessage: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Message Inbox",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${messages.size} message${if (messages.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateNewOrder) {
                        Icon(Icons.Default.Add, contentDescription = "Add Message")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateNewOrder,
                icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                text = { Text("Paste Message") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Status and source filters. Scrollable so adding a source later
            // does not squeeze the row.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to "All",
                    "NEEDS_REVIEW" to "Needs Review",
                    "CONVERTED" to "Converted",
                    "WHATSAPP" to "WhatsApp",
                    "SMS" to "SMS",
                    "NOTIFICATION" to "Notification",
                    "MANUAL" to "Manual",
                )
                items(filters, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { onFilterSelected(key) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        DevCraftMark(size = 56.dp)
                        Text(
                            text = "No Messages Found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Share an order text from WhatsApp or paste any conversational message to parse it offline.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateNewOrder,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste First Order")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(messages, key = { it.messageId }) { message ->
                        // Newly captured messages slide in rather than snapping,
                        // which matters when one arrives while you are looking.
                        MessageCard(
                            message = message,
                            onClick = { onNavigateDetail(message.messageId) },
                            onDelete = { onDeleteMessage(message.messageId) },
                            modifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = 250)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCard(
    message: MessageEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConverted = message.status == "CONVERTED"
    val isWhatsApp = message.source == MessageSource.WHATSAPP_SHARE.name

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConverted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Source Chip, Sender, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SourceBadge(source = message.source)

                    Text(
                        text = message.senderName ?: message.sender ?: "Unknown Sender",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = formatTimestamp(message.receivedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Raw Text Snippet
            Text(
                text = "\"${message.originalText}\"",
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Category Badge, Status Badge + Interpret/View CTA
            val isOrder = message.classification == "ORDER" ||
                (message.classification == "UNKNOWN" && (message.originalText.contains("food", ignoreCase = true) || message.originalText.contains("parcel", ignoreCase = true) || message.originalText.contains("chahiye", ignoreCase = true)))
            
            val categoryLabel = when (message.classification) {
                "ORDER" -> "ORDER"
                "DOCUMENT_FILE" -> "DOCUMENT"
                "BANK_FINANCIAL" -> "BANK"
                "OTP_AUTHENTICATION" -> "OTP"
                "DELIVERY_TRACKING" -> "DELIVERY"
                "PERSONAL_MESSAGE" -> "PERSONAL"
                "PROMOTIONAL" -> "PROMOTION"
                "SYSTEM_NOTIFICATION" -> "SYSTEM"
                else -> if (message.originalText.contains(".pdf", ignoreCase = true) || message.originalText.contains("pages", ignoreCase = true)) "DOCUMENT"
                else if (message.originalText.contains("otp", ignoreCase = true)) "OTP"
                else if (message.originalText.contains("credited", ignoreCase = true) || message.originalText.contains("debited", ignoreCase = true)) "BANK"
                else if (isOrder) "ORDER"
                else "UNKNOWN"
            }

            val categoryColor = when (categoryLabel) {
                "ORDER" -> Color(0xFF2E7D32)
                "BANK" -> Color(0xFF1565C0)
                "OTP" -> Color(0xFFC62828)
                "DOCUMENT" -> Color(0xFF6A1B9A)
                "DELIVERY" -> Color(0xFF00838F)
                "PROMOTION" -> Color(0xFFE65100)
                else -> Color(0xFF546E7A)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category + Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = categoryLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isOrder) {
                        MessageStatusChip(status = message.status)
                    } else {
                        Text(
                            text = "Not an order",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    if (isConverted) {
                        TextButton(onClick = onClick) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Order", fontSize = 13.sp)
                        }
                    } else if (isOrder) {
                        Button(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Interpret", fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("View Message", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


private fun formatTimestamp(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(millis))
    }
}
