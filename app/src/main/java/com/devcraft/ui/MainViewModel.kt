package com.devcraft.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcraft.DevCraftApplication
import com.devcraft.alerts.LocalAlertScheduler
import com.devcraft.data.local.dao.OrderWithItems
import com.devcraft.data.local.entities.*
import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.sync.engine.OperationLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as DevCraftApplication).database
    private val orderDao = db.orderDao()
    private val customerDao = db.customerDao()
    private val operationDao = db.operationDao()
    private val conflictDao = db.conflictDao()
    private val messageDao = db.messageDao()
    private val alertScheduler = LocalAlertScheduler(application)

    val deviceId = "DEVICE_" + UUID.randomUUID().toString().take(8)
    private val operationLogManager = OperationLogManager(operationDao, deviceId)

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Navigation event for shared message ingestion
    private val _navigateToMessageDetail = MutableSharedFlow<String>()
    val navigateToMessageDetail: SharedFlow<String> = _navigateToMessageDetail.asSharedFlow()

    // Message Flows
    val allMessages: StateFlow<List<MessageEntity>> = messageDao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadMessageCount: StateFlow<Int> = messageDao.getUnreadMessageCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reviewNeededMessages: StateFlow<List<MessageEntity>> = messageDao.getReviewNeededMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messageFilter = MutableStateFlow("ALL") // ALL, NEEDS_REVIEW, CONVERTED
    val messageFilter: StateFlow<String> = _messageFilter.asStateFlow()

    val filteredMessages: StateFlow<List<MessageEntity>> = combine(
        allMessages,
        _messageFilter
    ) { messages, filter ->
        when (filter) {
            "NEEDS_REVIEW" -> messages.filter { it.status in listOf("RECEIVED", "PARSED", "REVIEWED") || it.needsClarification }
            "CONVERTED" -> messages.filter { it.status == "CONVERTED" }
            else -> messages
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Order & Conflict Flows
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

    val messageSearchResults: StateFlow<List<MessageEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else messageDao.searchMessages(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleNetworkStatus() {
        _isOnline.value = !_isOnline.value
    }

    fun setMessageFilter(filter: String) {
        _messageFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun parseMessage(text: String): ParsedMessage {
        return DeterministicParser.parse(text)
    }

    fun getMessageByIdFlow(id: String): Flow<MessageEntity?> {
        return messageDao.getMessageByIdFlow(id)
    }

    fun getOrderWithItemsFlow(orderId: String): Flow<OrderWithItems?> = flow {
        val result = orderDao.getOrderWithItemsById(orderId)
        emit(result)
    }

    suspend fun getMessageById(id: String): MessageEntity? {
        return withContext(Dispatchers.IO) {
            messageDao.getMessageById(id)
        }
    }

    suspend fun getOrderById(orderId: String): OrderWithItems? {
        return withContext(Dispatchers.IO) {
            orderDao.getOrderWithItemsById(orderId)
        }
    }

    /**
     * Ingests a shared message (from WhatsApp Share Intent, SMS, or Manual entry),
     * runs the offline deterministic parser immediately, and emits navigation event.
     */
    fun ingestSharedMessage(
        text: String,
        source: String = MessageSource.WHATSAPP_SHARE.name,
        sender: String? = null,
        senderName: String? = null
    ) {
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val messageId = UUID.randomUUID().toString()
            val parsed = DeterministicParser.parse(text)
            
            // Infer senderName from text if not provided
            val resolvedSenderName = senderName ?: parsed.customer

            val entity = MessageEntity(
                messageId = messageId,
                source = source,
                sender = sender,
                senderName = resolvedSenderName,
                originalText = text,
                receivedAt = System.currentTimeMillis(),
                status = MessageStatus.PARSED.name,
                confidence = parsed.confidence,
                needsClarification = parsed.needs_clarification,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            messageDao.insertMessage(entity)

            operationLogManager.logOperation(
                entityType = "MESSAGE",
                entityId = messageId,
                operationType = "CREATE",
                changedFieldsJson = "{\"source\": \"$source\", \"status\": \"PARSED\"}"
            )

            _navigateToMessageDetail.emit(messageId)
        }
    }

    /**
     * Converts a reviewed/parsed message into a confirmed Order and OrderItems in Room,
     * links the message, appends operation logs, and schedules a local due-date alert.
     */
    fun convertMessageToOrder(
        messageId: String,
        customerName: String,
        dueDate: String?,
        amount: Double?,
        items: List<ParsedItem>,
        rawMessage: String,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val orderId = UUID.randomUUID().toString()
            val resolvedCustomerName = if (customerName.isNotBlank()) customerName else "Guest Customer"

            // 1. Create/find customer
            val existingCustomers = customerDao.searchCustomers(resolvedCustomerName).firstOrNull()
            val customerId = existingCustomers?.firstOrNull { it.name.equals(resolvedCustomerName, ignoreCase = true) }?.customerId
                ?: UUID.randomUUID().toString().also { newCustId ->
                    customerDao.insertCustomer(
                        CustomerEntity(
                            customerId = newCustId,
                            name = resolvedCustomerName,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    operationLogManager.logOperation(
                        entityType = "CUSTOMER",
                        entityId = newCustId,
                        operationType = "CREATE",
                        changedFieldsJson = "{\"name\": \"$resolvedCustomerName\"}"
                    )
                }

            // 2. Insert OrderEntity
            val orderEntity = OrderEntity(
                orderId = orderId,
                customerId = customerId,
                customerName = resolvedCustomerName,
                status = "CONFIRMED",
                totalAmount = amount ?: 0.0,
                dueDate = dueDate,
                rawMessage = rawMessage,
                referencesPriorOrder = false,
                confidence = 1.0f,
                needsClarification = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            orderDao.insertOrder(orderEntity)

            // 3. Insert OrderItems
            val orderItems = items.map { item ->
                OrderItemEntity(
                    itemId = UUID.randomUUID().toString(),
                    orderId = orderId,
                    description = item.description,
                    quantity = item.quantity,
                    attributesJson = item.attributes.toString()
                )
            }
            orderDao.insertOrderItems(orderItems)

            // 4. Link MessageEntity
            messageDao.linkParsedOrder(
                messageId = messageId,
                orderId = orderId,
                status = MessageStatus.CONVERTED.name,
                updatedAt = System.currentTimeMillis()
            )

            // 5. Operation Logs
            operationLogManager.logOperation(
                entityType = "ORDER",
                entityId = orderId,
                operationType = "CREATE",
                changedFieldsJson = "{\"customerName\": \"$resolvedCustomerName\", \"status\": \"CONFIRMED\", \"totalAmount\": ${amount ?: 0.0}}"
            )
            operationLogManager.logOperation(
                entityType = "MESSAGE",
                entityId = messageId,
                operationType = "UPDATE",
                changedFieldsJson = "{\"status\": \"CONVERTED\", \"parsedOrderId\": \"$orderId\"}"
            )

            // 6. Schedule Local Alert if due date present
            if (!dueDate.isNullOrBlank()) {
                scheduleAlertForDueDate(orderId, resolvedCustomerName, dueDate)
            }

            withContext(Dispatchers.Main) {
                onComplete(orderId)
            }
        }
    }

    private fun scheduleAlertForDueDate(orderId: String, customerName: String, dueDateStr: String) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dueDateStr)
            if (date != null) {
                val cal = Calendar.getInstance().apply {
                    time = date
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val triggerMillis = cal.timeInMillis
                if (triggerMillis > System.currentTimeMillis()) {
                    alertScheduler.scheduleDueNotification(orderId, customerName, triggerMillis)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createOrderFromParsed(parsedMessage: ParsedMessage, rawMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            orderDao.deleteOrderComplete(orderId)
            operationLogManager.logOperation(
                entityType = "ORDER",
                entityId = orderId,
                operationType = "DELETE",
                changedFieldsJson = "{}"
            )
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.deleteMessageById(messageId)
            operationLogManager.logOperation(
                entityType = "MESSAGE",
                entityId = messageId,
                operationType = "DELETE",
                changedFieldsJson = "{}"
            )
        }
    }
}
