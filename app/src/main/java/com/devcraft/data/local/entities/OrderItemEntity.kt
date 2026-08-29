package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey val itemId: String = UUID.randomUUID().toString(),
    val orderId: String,
    val description: String,
    val quantity: Int = 1,
    val unitPrice: Double? = null,
    val attributesJson: String = "{}"
)
