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
import com.devcraft.ui.theme.DevCraftLockup

@Composable
fun DashboardScreen(
    isOnline: Boolean,
    totalOrders: Int,
    unreadMessageCount: Int,
    pendingSyncCount: Int,
    conflictCount: Int,
    dueTodayCount: Int,
    overdueCount: Int,
    outstandingTotal: Double,
    committedThisWeekCount: Int,
    committedThisWeekValue: Double,
    signedInAs: String? = null,
    canSignOut: Boolean = false,
    onSignOut: () -> Unit = {},
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
            DevCraftLockup(markSize = 40.dp, nameSize = 22.sp)
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

        if (canSignOut) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = signedInAs?.let { "Signed in as $it" } ?: "Working offline (not signed in)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSignOut) {
                    Text(if (signedInAs != null) "Sign out" else "Sign in", fontSize = 13.sp)
                }
            }
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

        // Operational answers, all from local SQL - no network involved.
        Text(text = "Today's Position", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OperationalTile(
                label = "Due Today",
                value = "$dueTodayCount",
                emphasis = dueTodayCount > 0,
                modifier = Modifier.weight(1f),
                onClick = onNavigateOrders,
            )
            OperationalTile(
                label = "Overdue",
                value = "$overdueCount",
                emphasis = overdueCount > 0,
                isAlert = overdueCount > 0,
                modifier = Modifier.weight(1f),
                onClick = onNavigateOrders,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OperationalTile(
                label = "Outstanding",
                value = "₹${"%,.0f".format(outstandingTotal)}",
                modifier = Modifier.weight(1f),
                onClick = onNavigateOrders,
            )
            OperationalTile(
                label = "Committed This Week",
                value = "$committedThisWeekCount · ₹${"%,.0f".format(committedThisWeekValue)}",
                modifier = Modifier.weight(1f),
                onClick = onNavigateOrders,
            )
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
private fun OperationalTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    isAlert: Boolean = false,
    onClick: () -> Unit = {},
) {
    val container = when {
        isAlert -> MaterialTheme.colorScheme.errorContainer
        emphasis -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
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
