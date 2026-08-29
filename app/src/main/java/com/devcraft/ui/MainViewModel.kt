package com.devcraft.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcraft.DevCraftApplication
import com.devcraft.data.local.dao.OrderWithItems
import com.devcraft.data.local.entities.*
import com.devcraft.domain.model.ParsedMessage
import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.sync.engine.OperationLogManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as DevCraftApplication).database
    private val orderDao = db.orderDao()
    private val customerDao = db.customerDao()
    private val operationDao = db.operationDao()
    private val conflictDao = db.conflictDao()

    val deviceId = "DEVICE_" + UUID.randomUUID().toString().take(8)
    private val operationLogManager = OperationLogManager(operationDao, deviceId)

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val orders: StateFlow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = customerDao.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingOperations: StateFlow<List<OperationEntity>> = operationDao.getPendingOperations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conflicts: StateFlow<List<ConflictEntity>> = conflictDao.getAllConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<OrderWithItems>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else orderDao.searchOrders(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleNetworkStatus() {
        _isOnline.value = !_isOnline.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun parseMessage(text: String): ParsedMessage {
        return DeterministicParser.parse(text)
    }

    fun createOrderFromParsed(parsedMessage: ParsedMessage, rawMessage: String) {
        viewModelScope.launch {
            val orderId = UUID.randomUUID().toString()
            val customerName = parsedMessage.customer ?: "Guest Customer"

            val orderEntity = OrderEntity(
                orderId = orderId,
                customerName = customerName,
                status = "CONFIRMED",
                totalAmount = parsedMessage.amount ?: 0.0,
                dueDate = parsedMessage.due_date,
                rawMessage = rawMessage,
                referencesPriorOrder = parsedMessage.references_prior_order,
                confidence = parsedMessage.confidence,
                needsClarification = parsedMessage.needs_clarification
            )

            val items = parsedMessage.items.map {
                OrderItemEntity(
                    orderId = orderId,
                    description = it.description,
                    quantity = it.quantity,
                    attributesJson = it.attributes.toString()
                )
            }

            orderDao.insertOrder(orderEntity)
            orderDao.insertOrderItems(items)

            operationLogManager.logOperation(
                entityType = "ORDER",
                entityId = orderId,
                operationType = "CREATE",
                changedFieldsJson = "{\"customerName\": \"$customerName\", \"status\": \"CONFIRMED\"}"
            )
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            val existing = orderDao.getOrderWithItemsById(orderId) ?: return@launch
            val updated = existing.order.copy(status = newStatus, updatedAt = System.currentTimeMillis())
            orderDao.updateOrder(updated)

            operationLogManager.logOperation(
                entityType = "ORDER",
                entityId = orderId,
                operationType = "UPDATE",
                changedFieldsJson = "{\"status\": \"$newStatus\"}"
            )
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            orderDao.deleteOrderComplete(orderId)
            operationLogManager.logOperation(
                entityType = "ORDER",
                entityId = orderId,
                operationType = "DELETE",
                changedFieldsJson = "{}"
            )
        }
    }
}
