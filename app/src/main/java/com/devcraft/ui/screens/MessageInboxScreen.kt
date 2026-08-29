package com.devcraft.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { onFilterSelected("ALL") },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == "NEEDS_REVIEW",
                    onClick = { onFilterSelected("NEEDS_REVIEW") },
                    label = { Text("Needs Review") }
                )
                FilterChip(
                    selected = selectedFilter == "CONVERTED",
                    onClick = { onFilterSelected("CONVERTED") },
                    label = { Text("Converted") }
                )
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
                        Icon(
                            imageVector = Icons.Default.MarkChatUnread,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "No Messages Found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Share an order text from WhatsApp or paste any conversational message to parse it offline.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.ui.text.style.TextAlign.Center
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
                        MessageCard(
                            message = message,
                            onClick = { onNavigateDetail(message.messageId) },
                            onDelete = { onDeleteMessage(message.messageId) }
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
    onDelete: () -> Unit
) {
    val isConverted = message.status == "CONVERTED"
    val isWhatsApp = message.source == MessageSource.WHATSAPP_SHARE.name

    Card(
        modifier = Modifier
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
                    // Source Icon/Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isWhatsApp) Color(0xFF25D366).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isWhatsApp) "WhatsApp" else message.source,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWhatsApp) Color(0xFF075E54) else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

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

            // Footer: Status Badge + Interpret/View CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusChip(status = message.status)
                    if (message.confidence > 0f) {
                        Text(
                            text = "${(message.confidence * 100).toInt()}% conf",
                            fontSize = 11.sp,
                            color = if (message.confidence >= 0.8f) Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold
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
                    } else {
                        Button(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Interpret", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "CONVERTED" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Converted")
        "PARSED" -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Parsed")
        "REVIEWED" -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Reviewed")
        else -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), "Received")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
