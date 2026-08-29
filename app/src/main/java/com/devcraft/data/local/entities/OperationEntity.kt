package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey val operationId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val entityType: String, // CUSTOMER, ORDER, ORDER_ITEM
    val entityId: String,
    val operationType: String, // CREATE, UPDATE, DELETE
    val changedFieldsJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING" // PENDING, IN_FLIGHT, SYNCED, FAILED
)
