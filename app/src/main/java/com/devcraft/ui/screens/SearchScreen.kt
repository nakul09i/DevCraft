package com.devcraft.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.dao.OrderWithItems
import com.devcraft.data.local.entities.MessageEntity
import com.devcraft.ui.components.MessageStatusChip
import com.devcraft.ui.components.OrderStatusPill
import com.devcraft.ui.components.SourceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    orderResults: List<OrderWithItems>,
    messageResults: List<MessageEntity>,
    onQueryChange: (String) -> Unit,
    onNavigateOrderDetail: (String) -> Unit,
    onNavigateMessageDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Search", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search orders & messages") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (query.isBlank()) {
                Text(
                    text = "Type a customer name, phone, item keyword, or message snippet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (orderResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Orders (${orderResults.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(orderResults, key = { "order_${it.order.orderId}" }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateOrderDetail(item.order.orderId) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.order.customerName ?: "Guest", fontWeight = FontWeight.Bold)
                                        OrderStatusPill(status = item.order.status)
                                    }
                                    if (!item.order.rawMessage.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("\"${item.order.rawMessage}\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    if (messageResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Messages (${messageResults.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(messageResults, key = { "msg_${it.messageId}" }) { msg ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateMessageDetail(msg.messageId) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(msg.senderName ?: msg.sender ?: "Message", fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            SourceBadge(source = msg.source)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            MessageStatusChip(status = msg.status)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("\"${msg.originalText}\"", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (orderResults.isEmpty() && messageResults.isEmpty()) {
                        item {
                            Text(
                                text = "No matching orders or messages found for \"$query\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
