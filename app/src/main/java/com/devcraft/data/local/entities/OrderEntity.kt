package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String = UUID.randomUUID().toString(),
    val customerId: String? = null,
    val customerName: String? = null,
    val status: String = "PENDING", // PENDING, CONFIRMED, PROCESSING, COMPLETED, CANCELLED
    val totalAmount: Double? = null,
    val dueDate: String? = null, // ISO-8601 date (YYYY-MM-DD)
    val rawMessage: String? = null,
    val referencesPriorOrder: Boolean = false,
    val confidence: Float = 1.0f,
    val needsClarification: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
