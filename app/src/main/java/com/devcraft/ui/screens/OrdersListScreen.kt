package com.devcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.dao.OrderWithItems

@Composable
fun OrdersListScreen(
    orders: List<OrderWithItems>,
    onDeleteOrder: (String) -> Unit,
    onUpdateStatus: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Orders (${orders.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNavigateBack) { Text("Back") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (orders.isEmpty()) {
            Text("No orders created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(orders) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Customer: ${item.order.customerName}", fontWeight = FontWeight.Bold)
                            Text("Status: ${item.order.status}")
                            Text("Due Date: ${item.order.dueDate ?: "None"}")
                            Text("Message: \"${item.order.rawMessage}\"", fontSize = 12.sp)

                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { onUpdateStatus(item.order.orderId, "COMPLETED") }) {
                                    Text("Complete")
                                }
                                TextButton(onClick = { onDeleteOrder(item.order.orderId) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
