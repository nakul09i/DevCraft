package com.devcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.dao.OrderWithItems
import com.devcraft.ui.components.OrderStatusPill
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderWithItems: OrderWithItems?,
    onUpdateStatus: (String, String) -> Unit,
    onDeleteOrder: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    if (orderWithItems == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val order = orderWithItems.order
    val items = orderWithItems.items

    var showAnalysisSheet by remember { mutableStateOf(false) }

    if (showAnalysisSheet && !order.rawMessage.isNullOrBlank()) {
        val parsed = remember(order.rawMessage) {
            com.devcraft.parser.offline.DeterministicParser.parse(order.rawMessage)
        }
        com.devcraft.ui.components.ScoreAnalysisSheet(
            parsed = parsed,
            onDismiss = { showAnalysisSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Details", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!order.rawMessage.isNullOrBlank()) {
                        IconButton(onClick = { showAnalysisSheet = true }) {
                            Icon(Icons.Default.Assessment, contentDescription = "View Scoring Analysis", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = {
                        onDeleteOrder(order.orderId)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Order", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = order.customerName ?: "Guest Customer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Created ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(order.createdAt))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OrderStatusPill(status = order.status)
                }
            }

            // Quick Metrics Row (Due Date, Total Amount)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Due Date", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = com.devcraft.parser.offline.DeterministicParser.displayDate(order.dueDate) ?: order.dueDate ?: "Not specified",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = order.totalAmount?.let { "₹%,.0f".format(it) } ?: "₹0",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }


            // Delivery location, when the message contained one
            if (!order.formattedAddress.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DELIVERY ADDRESS",
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = order.formattedAddress, fontSize = 14.sp)
                            if (order.latitude == null) {
                                Text(
                                    text = "Text only - map coordinates need a Mappls key",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Order Items Section
            Text("Order Items (${items.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            items.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${item.quantity}x ${item.description}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (item.attributesJson.isNotBlank() && item.attributesJson != "{}") {
                                Text(
                                    text = item.attributesJson,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Preserved Original Message Card
            if (!order.rawMessage.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ORIGINAL RAW MESSAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${order.rawMessage}\"",
                            fontSize = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Status Update Action Row
            Text("Update Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val statuses = listOf("CONFIRMED", "PROCESSING", "COMPLETED", "CANCELLED")
                statuses.forEach { status ->
                    val isSelected = order.status == status
                    OutlinedButton(
                        onClick = { onUpdateStatus(order.orderId, status) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        colors = if (isSelected)
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else
                            ButtonDefaults.outlinedButtonColors()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                statusIcon(status),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = status.take(4),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Status buttons carry their lifecycle icon so the row is scannable. */
@Composable
private fun statusIcon(status: String) = when (status) {
    "COMPLETED" -> Icons.Default.CheckCircle
    "PROCESSING" -> Icons.Default.LocalShipping
    "CANCELLED" -> Icons.Default.Cancel
    else -> Icons.Default.TaskAlt
}
