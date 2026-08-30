package com.devcraft

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devcraft.data.local.entities.MessageSource
import com.devcraft.ui.MainViewModel
import com.devcraft.ui.components.DevCraftBottomNavBar
import com.devcraft.ui.screens.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isOnline by viewModel.isOnline.collectAsState()
                val orders by viewModel.orders.collectAsState()
                val pendingOps by viewModel.pendingOperations.collectAsState()
                val conflicts by viewModel.conflicts.collectAsState()
                val unreadMsgCount by viewModel.unreadMessageCount.collectAsState()
                val filteredMessages by viewModel.filteredMessages.collectAsState()
                val selectedFilter by viewModel.messageFilter.collectAsState()

                // Handle incoming shared message navigation
                LaunchedEffect(Unit) {
                    viewModel.navigateToMessageDetail.collect { messageId ->
                        navController.navigate("message_detail/$messageId")
                    }
                }

                Scaffold(
                    bottomBar = {
                        // Only show bottom navigation on top-level destinations
                        val topLevelRoutes = listOf("inbox", "orders", "dashboard", "search", "conflicts")
                        if (currentRoute in topLevelRoutes) {
                            DevCraftBottomNavBar(
                                currentRoute = currentRoute,
                                unreadMessageCount = unreadMsgCount,
                                conflictCount = conflicts.size,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("inbox") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "inbox",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("inbox") {
                            MessageInboxScreen(
                                messages = filteredMessages,
                                selectedFilter = selectedFilter,
                                onFilterSelected = { viewModel.setMessageFilter(it) },
                                onNavigateDetail = { id -> navController.navigate("message_detail/$id") },
                                onNavigateNewOrder = { navController.navigate("new_order") },
                                onDeleteMessage = { id -> viewModel.deleteMessage(id) }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                isOnline = isOnline,
                                totalOrders = orders.size,
                                unreadMessageCount = unreadMsgCount,
                                pendingSyncCount = pendingOps.size,
                                conflictCount = conflicts.size,
                                onToggleNetwork = { viewModel.toggleNetworkStatus() },
                                onNavigateInbox = { navController.navigate("inbox") },
                                onNavigateNewOrder = { navController.navigate("new_order") },
                                onNavigateOrders = { navController.navigate("orders") },
                                onNavigateSearch = { navController.navigate("search") },
                                onNavigateConflicts = { navController.navigate("conflicts") }
                            )
                        }

                        composable("orders") {
                            OrdersListScreen(
                                orders = orders,
                                onNavigateOrderDetail = { id -> navController.navigate("order_detail/$id") },
                                onDeleteOrder = { viewModel.deleteOrder(it) },
                                onUpdateStatus = { id, status -> viewModel.updateOrderStatus(id, status) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("search") {
                            val searchQuery by viewModel.searchQuery.collectAsState()
                            val searchResults by viewModel.searchResults.collectAsState()
                            val messageResults by viewModel.messageSearchResults.collectAsState()

                            SearchScreen(
                                query = searchQuery,
                                orderResults = searchResults,
                                messageResults = messageResults,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                onNavigateOrderDetail = { id -> navController.navigate("order_detail/$id") },
                                onNavigateMessageDetail = { id -> navController.navigate("message_detail/$id") },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("conflicts") {
                            ConflictsScreen(
                                conflicts = conflicts,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("new_order") {
                            NewOrderScreen(
                                onParseMessage = { viewModel.parseMessage(it) },
                                onSaveToInbox = { text, source ->
                                    viewModel.ingestSharedMessage(text = text, source = source)
                                },
                                onConfirmOrder = { parsed, raw -> viewModel.createOrderFromParsed(parsed, raw) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "message_detail/{messageId}",
                            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
                            val message by viewModel.getMessageByIdFlow(messageId).collectAsState(initial = null)

                            MessageDetailScreen(
                                message = message,
                                onParseText = { viewModel.parseMessage(it) },
                                onConfirmOrder = { msgId, custName, due, amt, items, raw, onDone ->
                                    viewModel.convertMessageToOrder(msgId, custName, due, amt, items, raw, onDone)
                                },
                                onNavigateOrderDetail = { orderId ->
                                    navController.navigate("order_detail/$orderId") {
                                        popUpTo("inbox")
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "order_detail/{orderId}",
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                            val orderWithItems by viewModel.getOrderWithItemsFlow(orderId).collectAsState(initial = null)

                            OrderDetailScreen(
                                orderWithItems = orderWithItems,
                                onUpdateStatus = { id, status -> viewModel.updateOrderStatus(id, status) },
                                onDeleteOrder = { id -> viewModel.deleteOrder(id) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.ingestSharedMessage(
                    text = sharedText,
                    source = MessageSource.WHATSAPP_SHARE.name,
                    senderName = subject
                )
            }
        }
    }
}
