package com.devcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devcraft.ui.MainViewModel
import com.devcraft.ui.screens.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val isOnline by viewModel.isOnline.collectAsState()
                val orders by viewModel.orders.collectAsState()
                val pendingOps by viewModel.pendingOperations.collectAsState()
                val conflicts by viewModel.conflicts.collectAsState()

                Scaffold { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                isOnline = isOnline,
                                totalOrders = orders.size,
                                pendingSyncCount = pendingOps.size,
                                conflictCount = conflicts.size,
                                onToggleNetwork = { viewModel.toggleNetworkStatus() },
                                onNavigateNewOrder = { navController.navigate("new_order") },
                                onNavigateOrders = { navController.navigate("orders") },
                                onNavigateSearch = { navController.navigate("search") },
                                onNavigateConflicts = { navController.navigate("conflicts") }
                            )
                        }

                        composable("new_order") {
                            NewOrderScreen(
                                onParseMessage = { viewModel.parseMessage(it) },
                                onConfirmOrder = { parsed, raw -> viewModel.createOrderFromParsed(parsed, raw) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("orders") {
                            OrdersListScreen(
                                orders = orders,
                                onDeleteOrder = { viewModel.deleteOrder(it) },
                                onUpdateStatus = { id, status -> viewModel.updateOrderStatus(id, status) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("search") {
                            val searchQuery by viewModel.searchQuery.collectAsState()
                            val searchResults by viewModel.searchResults.collectAsState()

                            SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("conflicts") {
                            ConflictsScreen(
                                conflicts = conflicts,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
