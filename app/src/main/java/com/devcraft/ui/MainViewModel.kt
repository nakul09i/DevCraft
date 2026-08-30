package com.devcraft.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.devcraft.DevCraftApplication
import com.devcraft.alerts.LocalAlertScheduler
import com.devcraft.core.AppSettings
import com.devcraft.core.CaptureDiagnostics
import com.devcraft.core.ConnectionState
import com.devcraft.core.ConnectivityObserver
import com.devcraft.core.SyncStatus
import com.devcraft.data.ingest.MessageIngestor
import com.devcraft.data.local.dao.CustomerBalance
import com.devcraft.data.local.dao.OrderWithItems
import com.devcraft.domain.OperationalCalendar
import com.devcraft.data.local.entities.*
import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.auth.FirebaseAuthRepository
import com.devcraft.sync.engine.OperationLogManager
import com.devcraft.sync.engine.SyncEngine
import com.devcraft.sync.engine.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as DevCraftApplication).database
    private val orderDao = db.orderDao()
    private val customerDao = db.customerDao()
    private val operationDao = db.operationDao()
    private val conflictDao = db.conflictDao()
    private val messageDao = db.messageDao()
    private val alertScheduler = LocalAlertScheduler(application)

    val deviceId = (application as DevCraftApplication).deviceId
    private val operationLogManager = OperationLogManager(operationDao, deviceId)
    private val messageIngestor = MessageIngestor(db, deviceId)
    private val authRepo = FirebaseAuthRepository(application)
    val syncEngine = SyncEngine(application, db, deviceId)

    val currentUserId = MutableStateFlow<String?>(authRepo.currentUser()?.uid ?: "merchant_default_store")

    // --- Real connectivity observer ---

    private val connectivity = ConnectivityObserver(application)
    val settings = AppSettings(application)

    val connectionState: StateFlow<ConnectionState> = connectivity.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), connectivity.current())

    val isOnline: StateFlow<Boolean> = connectionState
        .map { it == ConnectionState.ONLINE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True only while a sync is actually running. */
    private val _syncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = combine(
        connectionState, _syncing, _syncError
    ) { connection, syncing, error ->
        when {
            syncing -> SyncStatus.SYNCING
            error != null -> SyncStatus.SYNC_ERROR
            connection == ConnectionState.ONLINE -> SyncStatus.ONLINE
            else -> SyncStatus.OFFLINE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.OFFLINE)

    val smsCaptureEnabled: StateFlow<Boolean> = settings.smsCaptureEnabled
    val notificationCaptureEnabled: StateFlow<Boolean> = settings.notificationCaptureEnabled
    val lastSyncAt: StateFlow<Long?> = settings.lastSyncAt

    init {
        // Auto-sync when connectivity transitions to ONLINE
        viewModelScope.launch {
            connectionState.collect { conn ->
                if (conn == ConnectionState.ONLINE) {
                    val uid = currentUserId.value ?: authRepo.currentUser()?.uid ?: "merchant_default_store"
                    syncEngine.startRealtimeSync(uid)
                    requestSyncNow()
                }
            }
        }

        // Start realtime sync immediately
        val uid = authRepo.currentUser()?.uid ?: "merchant_default_store"
        onUserAuthenticated(uid)
    }

    fun onUserAuthenticated(uid: String) {
        currentUserId.value = uid
        syncEngine.startRealtimeSync(uid)
        SyncWorker.schedulePeriodicSync(getApplication())
        requestSyncNow()
    }

    fun onUserSignedOut() {
        currentUserId.value = "merchant_default_store"
        syncEngine.stopRealtimeSync()
    }

    fun setSmsCaptureEnabled(enabled: Boolean) = settings.setSmsCaptureEnabled(enabled)
    fun setNotificationCaptureEnabled(enabled: Boolean) =
        settings.setNotificationCaptureEnabled(enabled)

    private val _diagnostics = MutableStateFlow(settings.diagnostics())
    val diagnostics: StateFlow<CaptureDiagnostics> = _diagnostics.asStateFlow()

    fun refreshDiagnostics() {
        _diagnostics.value = settings.diagnostics()
    }

    private val _pendingMessageId = MutableStateFlow<String?>(null)
    val pendingMessageId: StateFlow<String?> = _pendingMessageId.asStateFlow()

    fun consumePendingMessage() {
        _pendingMessageId.value = null
    }

    fun openMessage(messageId: String) {
        _pendingMessageId.value = messageId
    }

    private val _pendingOrderId = MutableStateFlow<String?>(null)
    val pendingOrderId: StateFlow<String?> = _pendingOrderId.asStateFlow()

    fun openOrder(orderId: String) {
        _pendingOrderId.value = orderId
    }

    fun consumePendingOrder() {
        _pendingOrderId.value = null
    }

    // Message Flows
    val allMessages: StateFlow<List<MessageEntity>> = messageDao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadMessageCount: StateFlow<Int> = messageDao.getUnreadMessageCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reviewNeededMessages: StateFlow<List<MessageEntity>> = messageDao.getReviewNeededMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messageFilter = MutableStateFlow("ALL")
    val messageFilter: StateFlow<String> = _messageFilter.asStateFlow()

    val filteredMessages: StateFlow<List<MessageEntity>> = combine(
        allMessages,
        _messageFilter
    ) { messages, filter ->
        when (filter) {
            "NEEDS_REVIEW" -> messages.filter {
                it.status in listOf("RECEIVED", "PARSED", "REVIEWED") || it.needsClarification
            }
            "CONVERTED" -> messages.filter { it.status == "CONVERTED" }
            "WHATSAPP" -> messages.filter {
                it.source == MessageSource.WHATSAPP_SHARE.name ||
                    it.source == MessageSource.OTHER_SHARE.name
            }
            "SMS" -> messages.filter { it.source == MessageSource.SMS.name }
            "NOTIFICATION" -> messages.filter { it.source == MessageSource.NOTIFICATION.name }
            "MANUAL" -> messages.filter { it.source == MessageSource.MANUAL.name }
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

    val pendingOperationsCount: StateFlow<Int> = operationDao.getPendingOperationsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val conflicts: StateFlow<List<ConflictEntity>> = conflictDao.getAllConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val today = OperationalCalendar.today()
    private val weekWindow = OperationalCalendar.weekWindow()

    val dueToday: StateFlow<List<OrderWithItems>> = orderDao.getDueOn(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overdue: StateFlow<List<OrderWithItems>> = orderDao.getOverdue(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outstandingTotal: StateFlow<Double> = orderDao.getOutstandingTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val customerBalances: StateFlow<List<CustomerBalance>> = orderDao.getCustomerBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val committedThisWeekValue: StateFlow<Double> =
        orderDao.getCommittedValueBetween(weekWindow.first, weekWindow.second)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val committedThisWeekCount: StateFlow<Int> =
        orderDao.getCommittedCountBetween(weekWindow.first, weekWindow.second)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun lastOrderFor(customerName: String): OrderWithItems? =
        withContext(Dispatchers.IO) { orderDao.getLastOrderForCustomer(customerName) }

    fun ordersForCustomer(name: String): Flow<List<OrderWithItems>> =
        orderDao.getOrdersForCustomer(name)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<OrderWithItems>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else orderDao.searchOrders(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messageSearchResults: StateFlow<List<MessageEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else messageDao.searchMessages(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Triggers immediate synchronization with Firestore.
     */
    fun requestSyncNow() {
        val uid = currentUserId.value ?: authRepo.currentUser()?.uid ?: "merchant_default_store"
        if (!syncEngine.isConfigured) return

        viewModelScope.launch {
            _syncing.value = true
            _syncError.value = null
            val result = syncEngine.syncNow(uid)
            _syncing.value = false
            result.onFailure { t ->
                _syncError.value = t.message ?: "Sync failed. Will retry automatically."
            }
        }
    }

    fun clearSyncError() {
        _syncError.value = null
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

    fun getOrderWithItemsFlow(orderId: String): Flow<OrderWithItems?> =
        orderDao.getOrderWithItemsByIdFlow(orderId)

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

    fun ingestSharedMessage(
        text: String,
        source: String = MessageSource.WHATSAPP_SHARE.name,
        sender: String? = null,
        senderName: String? = null
    ) {
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val messageId = messageIngestor.ingest(
                text = text,
                source = source,
                sender = sender,
                senderName = senderName,
            )
            if (messageId != null) {
                _pendingMessageId.value = messageId
                requestSyncNow()
            }
        }
    }

    fun convertMessageToOrder(
        messageId: String,
        customerName: String,
        dueDate: String?,
        amount: Double?,
        items: List<ParsedItem>,
        rawMessage: String,
        deliveryAddress: String? = null,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val orderId = UUID.randomUUID().toString()
            val resolvedCustomerName = if (customerName.isNotBlank()) customerName else "Guest Customer"
            val now = System.currentTimeMillis()
            val uid = currentUserId.value ?: "merchant_default_store"

            db.withTransaction {
                val customerId = customerDao.findCustomerByName(resolvedCustomerName)?.customerId
                    ?: UUID.randomUUID().toString().also { newCustId ->
                        customerDao.insertCustomer(
                            CustomerEntity(
                                customerId = newCustId,
                                name = resolvedCustomerName,
                                createdAt = now
                            )
                        )
                        operationLogManager.logOperation(
                            entityType = "CUSTOMER",
                            entityId = newCustId,
                            operationType = "CREATE",
                            changedFieldsJson = "{\"name\": \"$resolvedCustomerName\"}"
                        )
                    }

                orderDao.insertOrder(
                    OrderEntity(
                        orderId = orderId,
                        orderNumber = "#${(1000..9999).random()}",
                        source = "SMS",
                        customerId = customerId,
                        customerName = resolvedCustomerName,
                        status = "CONFIRMED",
                        totalAmount = amount ?: 0.0,
                        dueDate = dueDate,
                        rawDateText = dueDate,
                        resolvedDate = dueDate,
                        dateConfidence = 1.0f,
                        rawMessage = rawMessage,
                        referencesPriorOrder = false,
                        confidence = 1.0f,
                        needsClarification = false,
                        classification = "ORDER",
                        classificationScore = 0.97f,
                        fieldExtractionScore = 0.95f,
                        dateResolutionScore = if (dueDate != null) 1.0f else 1.0f,
                        clarificationDecisionScore = 1.0f,
                        overallScore = 0.95f,
                        createdAt = now,
                        updatedAt = now,
                        deviceId = deviceId,
                        userId = uid,
                        formattedAddress = deliveryAddress,
                        locationSource = deliveryAddress?.let { "MESSAGE_TEXT" },
                        locationUpdatedAt = deliveryAddress?.let { now }
                    )
                )

                orderDao.insertOrderItems(
                    items.map { item ->
                        OrderItemEntity(
                            itemId = UUID.randomUUID().toString(),
                            orderId = orderId,
                            description = item.description,
                            quantity = item.quantity,
                            attributesJson = item.attributes.toString()
                        )
                    }
                )

                messageDao.linkParsedOrder(
                    messageId = messageId,
                    orderId = orderId,
                    status = MessageStatus.CONVERTED.name,
                    updatedAt = now
                )

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
            }

            alertScheduler.scheduleForDueDate(orderId, resolvedCustomerName, dueDate)

            // Trigger immediate sync to Firestore
            requestSyncNow()

            withContext(Dispatchers.Main) {
                onComplete(orderId)
            }
        }
    }

    fun createOrderFromParsed(parsedMessage: ParsedMessage, rawMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val orderId = UUID.randomUUID().toString()
            val customerName = parsedMessage.customer ?: "Guest Customer"
            val uid = currentUserId.value ?: "merchant_default_store"

            val orderEntity = OrderEntity(
                orderId = orderId,
                orderNumber = "#${(1000..9999).random()}",
                source = "SMS",
                customerName = customerName,
                status = "CONFIRMED",
                totalAmount = parsedMessage.amount ?: 0.0,
                dueDate = parsedMessage.due_date,
                rawDateText = parsedMessage.raw_date_text,
                resolvedDate = parsedMessage.due_date,
                dateConfidence = parsedMessage.date_confidence,
                rawMessage = rawMessage,
                referencesPriorOrder = parsedMessage.references_prior_order,
                confidence = parsedMessage.confidence,
                needsClarification = parsedMessage.needs_clarification,
                paymentMethod = parsedMessage.payment_method,
                classification = parsedMessage.classification.name,
                classificationScore = parsedMessage.classification_score,
                fieldExtractionScore = parsedMessage.field_extraction_score,
                dateResolutionScore = parsedMessage.date_resolution_score,
                clarificationDecisionScore = parsedMessage.clarification_decision_score,
                overallScore = parsedMessage.overall_score,
                deviceId = deviceId,
                userId = uid,
                formattedAddress = parsedMessage.delivery_address
            )

            val items = parsedMessage.items.map {
                OrderItemEntity(
                    orderId = orderId,
                    description = it.description,
                    quantity = it.quantity,
                    attributesJson = it.attributes.toString()
                )
            }

            db.withTransaction {
                orderDao.insertOrder(orderEntity)
                orderDao.insertOrderItems(items)
                operationLogManager.logOperation(
                    entityType = "ORDER",
                    entityId = orderId,
                    operationType = "CREATE",
                    changedFieldsJson = "{\"customerName\": \"$customerName\", \"status\": \"CONFIRMED\"}"
                )
            }

            alertScheduler.scheduleForDueDate(orderId, customerName, parsedMessage.due_date)

            // Trigger immediate sync
            requestSyncNow()
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                val existing = orderDao.getOrderWithItemsById(orderId) ?: return@withTransaction
                val updated = existing.order.copy(
                    status = newStatus,
                    version = existing.order.version + 1,
                    baseVersion = existing.order.version,
                    lastModifiedBy = deviceId,
                    updatedAt = System.currentTimeMillis()
                )
                orderDao.updateOrder(updated)
                operationLogManager.logOperation(
                    entityType = "ORDER",
                    entityId = orderId,
                    operationType = "STATUS_CHANGE",
                    changedFieldsJson = "{\"status\": \"$newStatus\"}"
                )
            }

            if (newStatus == "COMPLETED" || newStatus == "CANCELLED") {
                alertScheduler.cancel(orderId)
            }

            // Trigger immediate sync
            requestSyncNow()
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                val existing = orderDao.getOrderWithItemsById(orderId)
                if (existing != null) {
                    val tombstone = existing.order.copy(
                        isDeleted = true,
                        version = existing.order.version + 1,
                        baseVersion = existing.order.version,
                        lastModifiedBy = deviceId,
                        updatedAt = System.currentTimeMillis()
                    )
                    orderDao.updateOrder(tombstone)
                }
                operationLogManager.logOperation(
                    entityType = "ORDER",
                    entityId = orderId,
                    operationType = "DELETE",
                    changedFieldsJson = "{}"
                )
            }
            alertScheduler.cancel(orderId)

            // Trigger immediate sync
            requestSyncNow()
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                messageDao.deleteMessageById(messageId)
                operationLogManager.logOperation(
                    entityType = "MESSAGE",
                    entityId = messageId,
                    operationType = "DELETE",
                    changedFieldsJson = "{}"
                )
            }
            requestSyncNow()
        }
    }
}
