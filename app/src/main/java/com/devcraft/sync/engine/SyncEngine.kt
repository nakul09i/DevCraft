package com.devcraft.sync.engine

import android.content.Context
import androidx.room.withTransaction
import com.devcraft.core.AppSettings
import com.devcraft.data.local.database.DevCraftDatabase
import com.devcraft.data.local.entities.*
import com.devcraft.sync.conflict.DeterministicConflictResolver
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class SyncSummary(
    val uploadedOperations: Int = 0,
    val downloadedEntities: Int = 0,
    val loggedConflicts: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

class SyncEngine(
    private val context: Context,
    private val db: DevCraftDatabase,
    private val deviceId: String
) {
    private val orderDao = db.orderDao()
    private val customerDao = db.customerDao()
    private val messageDao = db.messageDao()
    private val operationDao = db.operationDao()
    private val conflictDao = db.conflictDao()
    private val settings = AppSettings(context)
    private val gson = Gson()

    private val isFirebaseAvailable: Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    private val firestore: FirebaseFirestore? by lazy {
        if (isFirebaseAvailable) {
            runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        } else {
            null
        }
    }

    private var ordersListener: ListenerRegistration? = null
    private var customersListener: ListenerRegistration? = null
    private var itemsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isConfigured: Boolean get() = firestore != null

    /**
     * Executes a full synchronization round:
     * 1. Uploads all local PENDING operations to Firestore
     * 2. Downloads all remote changes under users/{uid}/
     * 3. Merges remote entities into Room with version and conflict handling
     * 4. Updates lastSyncAt metadata
     */
    suspend fun syncNow(uid: String): Result<SyncSummary> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext Result.failure(
            IllegalStateException("Cloud sync is not configured. Firebase credentials absent.")
        )
        if (uid.isBlank()) return@withContext Result.failure(
            IllegalArgumentException("Cannot sync without authenticated user ID.")
        )

        runCatching {
            val uploadedCount = uploadPendingOperations(fs, uid)
            val downloadedCount = downloadRemoteEntities(fs, uid)
            val now = System.currentTimeMillis()
            settings.recordSuccessfulSync(now)

            SyncSummary(
                uploadedOperations = uploadedCount,
                downloadedEntities = downloadedCount,
                timestamp = now
            )
        }
    }

    /**
     * Uploads local PENDING operations to users/{uid}/operations/
     * and updates the corresponding entity document snapshot in Firestore.
     */
    suspend fun uploadPendingOperations(fs: FirebaseFirestore, uid: String): Int {
        val pending = operationDao.getPendingOperationsList()
        if (pending.isEmpty()) return 0

        val userDoc = fs.collection("users").document(uid)
        val operationsCol = userDoc.collection("operations")
        val ordersCol = userDoc.collection("orders")
        val customersCol = userDoc.collection("customers")
        val itemsCol = userDoc.collection("orderItems")
        val messagesCol = userDoc.collection("messages")

        val uploadedIds = mutableListOf<String>()

        for (op in pending) {
            // 1. Upload the operation journal entry (idempotent write)
            val opMap = hashMapOf(
                "operationId" to op.operationId,
                "deviceId" to op.deviceId,
                "userId" to uid,
                "entityType" to op.entityType,
                "entityId" to op.entityId,
                "operationType" to op.operationType,
                "version" to op.version,
                "baseVersion" to op.baseVersion,
                "changedFieldsJson" to op.changedFieldsJson,
                "timestamp" to op.timestamp,
                "hlcTimestamp" to op.hlcTimestamp,
                "logicalClock" to op.logicalClock,
                "createdAt" to op.createdAt
            )
            operationsCol.document(op.operationId).set(opMap, SetOptions.merge()).await()

            // 2. Reflect state onto the target entity collection
            when (op.entityType) {
                "ORDER" -> {
                    if (op.operationType == "DELETE") {
                        ordersCol.document(op.entityId).set(
                            mapOf(
                                "orderId" to op.entityId,
                                "isDeleted" to true,
                                "deletedAt" to op.timestamp,
                                "deletedByDeviceId" to op.deviceId,
                                "version" to op.version
                            ),
                            SetOptions.merge()
                        ).await()
                    } else {
                        val orderWithItems = orderDao.getOrderWithItemsById(op.entityId)
                        if (orderWithItems != null) {
                            val orderMap = orderEntityToMap(orderWithItems.order, uid)
                            ordersCol.document(op.entityId).set(orderMap, SetOptions.merge()).await()
                            // Also sync items
                            for (item in orderWithItems.items) {
                                val itemMap = orderItemEntityToMap(item)
                                itemsCol.document(item.itemId).set(itemMap, SetOptions.merge()).await()
                            }
                        }
                    }
                }
                "CUSTOMER" -> {
                    if (op.operationType == "DELETE") {
                        customersCol.document(op.entityId).set(
                            mapOf("customerId" to op.entityId, "isDeleted" to true, "deletedAt" to op.timestamp),
                            SetOptions.merge()
                        ).await()
                    } else {
                        val customer = customerDao.getCustomerById(op.entityId)
                        if (customer != null) {
                            customersCol.document(op.entityId).set(customerEntityToMap(customer), SetOptions.merge()).await()
                        }
                    }
                }
                "MESSAGE" -> {
                    if (op.operationType == "DELETE") {
                        messagesCol.document(op.entityId).set(
                            mapOf("messageId" to op.entityId, "isDeleted" to true, "deletedAt" to op.timestamp),
                            SetOptions.merge()
                        ).await()
                    } else {
                        val message = messageDao.getMessageById(op.entityId)
                        if (message != null) {
                            messagesCol.document(op.entityId).set(messageEntityToMap(message), SetOptions.merge()).await()
                        }
                    }
                }
            }

            uploadedIds.add(op.operationId)
        }

        // Mark local operations as SYNCED
        if (uploadedIds.isNotEmpty()) {
            operationDao.markOperationsSynced(uploadedIds)
        }

        return uploadedIds.size
    }

    /**
     * Downloads remote customers, orders, items, and messages under users/{uid}/
     * and performs entity-level merge into Room with conflict detection.
     */
    suspend fun downloadRemoteEntities(fs: FirebaseFirestore, uid: String): Int {
        val userDoc = fs.collection("users").document(uid)
        var count = 0

        // 1. Customers
        val remoteCustomers = userDoc.collection("customers").get().await()
        for (doc in remoteCustomers.documents) {
            val isDeleted = doc.getBoolean("isDeleted") == true
            val customerId = doc.getString("customerId") ?: doc.id
            if (isDeleted) {
                val existing = customerDao.getCustomerById(customerId)
                if (existing != null) customerDao.deleteCustomer(existing)
            } else {
                val customer = mapToCustomerEntity(doc)
                if (customer != null) {
                    customerDao.insertCustomer(customer)
                    count++
                }
            }
        }

        // 2. Orders
        val remoteOrders = userDoc.collection("orders").get().await()
        for (doc in remoteOrders.documents) {
            val isDeleted = doc.getBoolean("isDeleted") == true
            val orderId = doc.getString("orderId") ?: doc.id
            if (isDeleted) {
                db.withTransaction {
                    orderDao.deleteOrderComplete(orderId)
                }
            } else {
                val remoteOrder = mapToOrderEntity(doc)
                if (remoteOrder != null) {
                    db.withTransaction {
                        val localOrder = orderDao.getOrderWithItemsById(orderId)?.order
                        if (localOrder == null) {
                            orderDao.insertOrder(remoteOrder)
                            count++
                        } else {
                            // Version comparison
                            if (remoteOrder.version > localOrder.version) {
                                orderDao.updateOrder(remoteOrder)
                                count++
                            } else if (remoteOrder.version == localOrder.version && remoteOrder.updatedAt > localOrder.updatedAt) {
                                orderDao.updateOrder(remoteOrder)
                                count++
                            }
                        }
                        Unit
                    }
                }
            }
        }

        // 3. OrderItems
        val remoteItems = userDoc.collection("orderItems").get().await()
        val itemsToInsert = mutableListOf<OrderItemEntity>()
        for (doc in remoteItems.documents) {
            val isDeleted = doc.getBoolean("isDeleted") == true
            if (!isDeleted) {
                mapToOrderItemEntity(doc)?.let { itemsToInsert.add(it) }
            }
        }
        if (itemsToInsert.isNotEmpty()) {
            db.withTransaction {
                orderDao.insertOrderItems(itemsToInsert)
            }
            count += itemsToInsert.size
        }

        // 4. Messages
        val remoteMessages = userDoc.collection("messages").get().await()
        for (doc in remoteMessages.documents) {
            val isDeleted = doc.getBoolean("isDeleted") == true
            val messageId = doc.getString("messageId") ?: doc.id
            if (isDeleted) {
                messageDao.deleteMessageById(messageId)
            } else {
                val msg = mapToMessageEntity(doc)
                if (msg != null) {
                    messageDao.insertMessage(msg)
                    count++
                }
            }
        }

        return count
    }

    /**
     * Registers real-time Firestore snapshot listeners for live synchronization.
     */
    fun startRealtimeSync(uid: String, onUpdateReceived: (() -> Unit)? = null) {
        val fs = firestore ?: return
        if (uid.isBlank()) return

        stopRealtimeSync()

        val userDoc = fs.collection("users").document(uid)

        ordersListener = userDoc.collection("orders").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            syncScope.launch {
                for (doc in snapshots.documents) {
                    val isDeleted = doc.getBoolean("isDeleted") == true
                    val orderId = doc.getString("orderId") ?: doc.id
                    if (isDeleted) {
                        db.withTransaction { orderDao.deleteOrderComplete(orderId) }
                    } else {
                        mapToOrderEntity(doc)?.let { remoteOrder ->
                            db.withTransaction {
                                val localOrder = orderDao.getOrderWithItemsById(orderId)?.order
                                if (localOrder == null || remoteOrder.version >= localOrder.version) {
                                    orderDao.insertOrder(remoteOrder)
                                }
                                Unit
                            }
                        }
                    }
                }
                onUpdateReceived?.invoke()
            }
        }

        itemsListener = userDoc.collection("orderItems").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            syncScope.launch {
                val items = snapshots.documents.mapNotNull { doc ->
                    if (doc.getBoolean("isDeleted") != true) mapToOrderItemEntity(doc) else null
                }
                if (items.isNotEmpty()) {
                    db.withTransaction { orderDao.insertOrderItems(items) }
                }
                onUpdateReceived?.invoke()
            }
        }

        customersListener = userDoc.collection("customers").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            syncScope.launch {
                for (doc in snapshots.documents) {
                    val isDeleted = doc.getBoolean("isDeleted") == true
                    val customerId = doc.getString("customerId") ?: doc.id
                    if (isDeleted) {
                        customerDao.getCustomerById(customerId)?.let { customerDao.deleteCustomer(it) }
                    } else {
                        mapToCustomerEntity(doc)?.let { customerDao.insertCustomer(it) }
                    }
                }
                onUpdateReceived?.invoke()
            }
        }

        messagesListener = userDoc.collection("messages").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            syncScope.launch {
                for (doc in snapshots.documents) {
                    val isDeleted = doc.getBoolean("isDeleted") == true
                    val messageId = doc.getString("messageId") ?: doc.id
                    if (isDeleted) {
                        messageDao.deleteMessageById(messageId)
                    } else {
                        mapToMessageEntity(doc)?.let { messageDao.insertMessage(it) }
                    }
                }
                onUpdateReceived?.invoke()
            }
        }
    }

    fun stopRealtimeSync() {
        ordersListener?.remove()
        ordersListener = null
        itemsListener?.remove()
        itemsListener = null
        customersListener?.remove()
        customersListener = null
        messagesListener?.remove()
        messagesListener = null
    }

    // --- Entity Mappers ---

    private fun orderEntityToMap(order: OrderEntity, uid: String): HashMap<String, Any?> = hashMapOf(
        "orderId" to order.orderId,
        "orderNumber" to order.orderNumber,
        "source" to order.source,
        "customerId" to order.customerId,
        "customerName" to order.customerName,
        "phone" to order.phone,
        "status" to order.status,
        "totalAmount" to order.totalAmount,
        "dueDate" to order.dueDate,
        "deliveryTime" to order.deliveryTime,
        "rawDateText" to order.rawDateText,
        "resolvedDate" to order.resolvedDate,
        "dateConfidence" to order.dateConfidence,
        "rawMessage" to order.rawMessage,
        "referencesPriorOrder" to order.referencesPriorOrder,
        "confidence" to order.confidence,
        "needsClarification" to order.needsClarification,
        "paymentMethod" to order.paymentMethod,
        "paymentStatus" to order.paymentStatus,
        "classification" to order.classification,
        "classificationScore" to order.classificationScore,
        "fieldExtractionScore" to order.fieldExtractionScore,
        "dateResolutionScore" to order.dateResolutionScore,
        "clarificationDecisionScore" to order.clarificationDecisionScore,
        "overallScore" to order.overallScore,
        "targetDurationMinutes" to order.targetDurationMinutes,
        "version" to order.version,
        "baseVersion" to order.baseVersion,
        "deviceId" to order.deviceId,
        "userId" to uid,
        "isDeleted" to order.isDeleted,
        "syncState" to "SYNCED",
        "lastModifiedBy" to order.lastModifiedBy,
        "latitude" to order.latitude,
        "longitude" to order.longitude,
        "formattedAddress" to order.formattedAddress,
        "pinCode" to order.pinCode,
        "placeId" to order.placeId,
        "locationSource" to order.locationSource,
        "locationUpdatedAt" to order.locationUpdatedAt,
        "createdAt" to order.createdAt,
        "updatedAt" to order.updatedAt
    )

    private fun mapToOrderEntity(doc: DocumentSnapshot): OrderEntity? {
        val orderId = doc.getString("orderId") ?: doc.id
        return OrderEntity(
            orderId = orderId,
            orderNumber = doc.getString("orderNumber") ?: "#${orderId.take(4)}",
            source = doc.getString("source") ?: "SMS",
            customerId = doc.getString("customerId"),
            customerName = doc.getString("customerName") ?: "Guest Customer",
            phone = doc.getString("phone"),
            status = doc.getString("status") ?: "NEW",
            totalAmount = doc.getDouble("totalAmount") ?: 0.0,
            dueDate = doc.getString("dueDate"),
            deliveryTime = doc.getString("deliveryTime"),
            rawDateText = doc.getString("rawDateText"),
            resolvedDate = doc.getString("resolvedDate") ?: doc.getString("dueDate"),
            dateConfidence = (doc.getDouble("dateConfidence") ?: 1.0).toFloat(),
            rawMessage = doc.getString("rawMessage"),
            referencesPriorOrder = doc.getBoolean("referencesPriorOrder") ?: false,
            confidence = (doc.getDouble("confidence") ?: 1.0).toFloat(),
            needsClarification = doc.getBoolean("needsClarification") ?: false,
            paymentMethod = doc.getString("paymentMethod"),
            paymentStatus = doc.getString("paymentStatus") ?: "PENDING",
            classification = doc.getString("classification") ?: "ORDER",
            classificationScore = (doc.getDouble("classificationScore") ?: 1.0).toFloat(),
            fieldExtractionScore = (doc.getDouble("fieldExtractionScore") ?: 1.0).toFloat(),
            dateResolutionScore = (doc.getDouble("dateResolutionScore") ?: 1.0).toFloat(),
            clarificationDecisionScore = (doc.getDouble("clarificationDecisionScore") ?: 1.0).toFloat(),
            overallScore = (doc.getDouble("overallScore") ?: 1.0).toFloat(),
            targetDurationMinutes = (doc.getLong("targetDurationMinutes") ?: 30L).toInt(),
            version = (doc.getLong("version") ?: 1L).toInt(),
            baseVersion = (doc.getLong("baseVersion") ?: 0L).toInt(),
            deviceId = doc.getString("deviceId") ?: "remote-device",
            userId = doc.getString("userId") ?: "user-default",
            isDeleted = doc.getBoolean("isDeleted") ?: false,
            syncState = "SYNCED",
            lastModifiedBy = doc.getString("lastModifiedBy") ?: doc.getString("deviceId") ?: "remote",
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            formattedAddress = doc.getString("formattedAddress"),
            pinCode = doc.getString("pinCode"),
            placeId = doc.getString("placeId"),
            locationSource = doc.getString("locationSource"),
            locationUpdatedAt = doc.getLong("locationUpdatedAt"),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun orderItemEntityToMap(item: OrderItemEntity): HashMap<String, Any?> = hashMapOf(
        "itemId" to item.itemId,
        "orderId" to item.orderId,
        "description" to item.description,
        "quantity" to item.quantity,
        "attributesJson" to item.attributesJson,
        "isDeleted" to false
    )

    private fun mapToOrderItemEntity(doc: DocumentSnapshot): OrderItemEntity? {
        val itemId = doc.getString("itemId") ?: doc.id
        val orderId = doc.getString("orderId") ?: return null
        return OrderItemEntity(
            itemId = itemId,
            orderId = orderId,
            description = doc.getString("description") ?: "Item",
            quantity = (doc.getLong("quantity") ?: 1L).toInt(),
            attributesJson = doc.getString("attributesJson") ?: "{}"
        )
    }

    private fun customerEntityToMap(customer: CustomerEntity): HashMap<String, Any?> = hashMapOf(
        "customerId" to customer.customerId,
        "name" to customer.name,
        "phone" to customer.phone,
        "latitude" to customer.latitude,
        "longitude" to customer.longitude,
        "formattedAddress" to customer.formattedAddress,
        "placeId" to customer.placeId,
        "locationSource" to customer.locationSource,
        "locationUpdatedAt" to customer.locationUpdatedAt,
        "createdAt" to customer.createdAt,
        "version" to customer.version,
        "isDeleted" to customer.isDeleted
    )

    private fun mapToCustomerEntity(doc: DocumentSnapshot): CustomerEntity? {
        val customerId = doc.getString("customerId") ?: doc.id
        val name = doc.getString("name") ?: return null
        return CustomerEntity(
            customerId = customerId,
            name = name,
            phone = doc.getString("phone"),
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            formattedAddress = doc.getString("formattedAddress"),
            placeId = doc.getString("placeId"),
            locationSource = doc.getString("locationSource"),
            locationUpdatedAt = doc.getLong("locationUpdatedAt"),
            version = (doc.getLong("version") ?: 1L).toInt(),
            isDeleted = doc.getBoolean("isDeleted") ?: false,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun messageEntityToMap(msg: MessageEntity): HashMap<String, Any?> = hashMapOf(
        "messageId" to msg.messageId,
        "source" to msg.source,
        "sender" to msg.sender,
        "senderName" to msg.senderName,
        "originalText" to msg.originalText,
        "receivedAt" to msg.receivedAt,
        "status" to msg.status,
        "confidence" to msg.confidence,
        "parsedOrderId" to msg.parsedOrderId,
        "needsClarification" to msg.needsClarification,
        "classification" to msg.classification,
        "classificationScore" to msg.classificationScore,
        "fieldExtractionScore" to msg.fieldExtractionScore,
        "dateResolutionScore" to msg.dateResolutionScore,
        "clarificationDecisionScore" to msg.clarificationDecisionScore,
        "overallScore" to msg.overallScore,
        "rawDateText" to msg.rawDateText,
        "resolvedDate" to msg.resolvedDate,
        "parseError" to msg.parseError,
        "createdAt" to msg.createdAt,
        "updatedAt" to msg.updatedAt,
        "isDeleted" to false
    )

    private fun mapToMessageEntity(doc: DocumentSnapshot): MessageEntity? {
        val messageId = doc.getString("messageId") ?: doc.id
        val originalText = doc.getString("originalText") ?: return null
        return MessageEntity(
            messageId = messageId,
            source = doc.getString("source") ?: MessageSource.MANUAL.name,
            sender = doc.getString("sender"),
            senderName = doc.getString("senderName"),
            originalText = originalText,
            receivedAt = doc.getLong("receivedAt") ?: System.currentTimeMillis(),
            status = doc.getString("status") ?: MessageStatus.RECEIVED.name,
            confidence = (doc.getDouble("confidence") ?: 0.0).toFloat(),
            parsedOrderId = doc.getString("parsedOrderId"),
            needsClarification = doc.getBoolean("needsClarification") ?: false,
            classification = doc.getString("classification") ?: "UNKNOWN",
            classificationScore = (doc.getDouble("classificationScore") ?: 0.0).toFloat(),
            fieldExtractionScore = (doc.getDouble("fieldExtractionScore") ?: 0.0).toFloat(),
            dateResolutionScore = (doc.getDouble("dateResolutionScore") ?: 0.0).toFloat(),
            clarificationDecisionScore = (doc.getDouble("clarificationDecisionScore") ?: 0.0).toFloat(),
            overallScore = (doc.getDouble("overallScore") ?: 0.0).toFloat(),
            rawDateText = doc.getString("rawDateText"),
            resolvedDate = doc.getString("resolvedDate"),
            parseError = doc.getString("parseError"),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }
}
