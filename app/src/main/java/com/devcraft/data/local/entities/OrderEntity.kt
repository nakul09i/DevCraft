package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String = UUID.randomUUID().toString(),
    val orderNumber: String = "#${(1000..9999).random()}",
    val source: String = "SMS", // SMS, WEBSITE, MANUAL
    val customerId: String? = null,
    val customerName: String? = null,
    val phone: String? = null,
    val status: String = "NEW", // NEW, CONFIRMED, PROCESSING, READY, OUT_FOR_DELIVERY, COMPLETED, CANCELLED
    val totalAmount: Double? = null,
    val dueDate: String? = null, // Canonical ISO-8601 date (YYYY-MM-DD)
    val deliveryTime: String? = null,
    val rawDateText: String? = null,
    val resolvedDate: String? = null,
    val dateConfidence: Float = 1.0f,
    val rawMessage: String? = null,
    val referencesPriorOrder: Boolean = false,
    val confidence: Float = 1.0f,
    val needsClarification: Boolean = false,
    val paymentMethod: String? = null,
    val paymentStatus: String = "PENDING", // PENDING, PAID, FAILED, COD
    val classification: String? = "ORDER",
    val classificationScore: Float = 1.0f,
    val fieldExtractionScore: Float = 1.0f,
    val dateResolutionScore: Float = 1.0f,
    val clarificationDecisionScore: Float = 1.0f,
    val overallScore: Float = 1.0f,
    val targetDurationMinutes: Int = 30, // Domino-style SLA target
    val version: Int = 1,
    val baseVersion: Int = 0,
    val deviceId: String = "device-local",
    val userId: String = "user-default",
    val isDeleted: Boolean = false,
    val syncState: String = "SYNCED", // PENDING, SYNCING, SYNCED, CONFLICT, FAILED
    val lastModifiedBy: String = "device-local",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Delivery location (100% optional, works offline)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val formattedAddress: String? = null,
    val pinCode: String? = null,
    val placeId: String? = null,
    val locationSource: String? = null,
    val locationUpdatedAt: Long? = null
)
