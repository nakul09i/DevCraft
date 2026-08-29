package com.devcraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(
    isOnline: Boolean,
    totalOrders: Int,
    unreadMessageCount: Int,
    pendingSyncCount: Int,
    conflictCount: Int,
    onToggleNetwork: () -> Unit,
    onNavigateInbox: () -> Unit,
    onNavigateNewOrder: () -> Unit,
    onNavigateOrders: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateConflicts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "DevCraft Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(text = "Offline-First Order Manager", fontSize = 14.sp, color = Color.Gray)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Button(onClick = onToggleNetwork, modifier = Modifier.fillMaxWidth()) {
            Text(if (isOnline) "Simulate Offline Mode" else "Simulate Online Mode")
        }

        // Metrics Grid (2x2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Message Inbox", "$unreadMessageCount unread", Modifier.weight(1f), onNavigateInbox)
            MetricCard("Total Orders", "$totalOrders", Modifier.weight(1f), onNavigateOrders)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Pending Sync", "$pendingSyncCount", Modifier.weight(1f), {})
            MetricCard("Conflicts", "$conflictCount", Modifier.weight(1f), onNavigateConflicts)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick Actions
        Text(text = "Quick Actions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        Button(
            onClick = onNavigateInbox,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Open Message Inbox", fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = onNavigateNewOrder,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Parse New Message Order", fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = onNavigateOrders,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("View All Orders", fontSize = 15.sp)
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 12.sp, color = Color.Gray)
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
