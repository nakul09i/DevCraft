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
fun SearchScreen(
    query: String,
    results: List<OrderWithItems>,
    onQueryChange: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Offline Search", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNavigateBack) { Text("Back") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search by customer, status or message text") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.order.customerName ?: "Guest", fontWeight = FontWeight.Bold)
                        Text(item.order.rawMessage ?: "", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
