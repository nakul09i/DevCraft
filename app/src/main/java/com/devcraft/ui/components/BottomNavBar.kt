package com.devcraft.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Inbox : Screen("inbox", "Inbox", Icons.Default.Inbox)
    object Orders : Screen("orders", "Orders", Icons.Default.Assignment)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Conflicts : Screen("conflicts", "Conflicts", Icons.Default.Warning)
}

@Composable
fun DevCraftBottomNavBar(
    currentRoute: String?,
    unreadMessageCount: Int,
    conflictCount: Int,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        Screen.Inbox,
        Screen.Orders,
        Screen.Dashboard,
        Screen.Search,
        Screen.Conflicts
    )

    NavigationBar {
        items.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                icon = {
                    BadgedBox(
                        badge = {
                            if (screen == Screen.Inbox && unreadMessageCount > 0) {
                                Badge { Text("$unreadMessageCount") }
                            } else if (screen == Screen.Conflicts && conflictCount > 0) {
                                Badge { Text("$conflictCount") }
                            }
                        }
                    ) {
                        Icon(screen.icon, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title) },
                selected = selected,
                onClick = {
                    if (currentRoute != screen.route) {
                        onNavigate(screen.route)
                    }
                }
            )
        }
    }
}
