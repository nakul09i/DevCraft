package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey val operationId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val userId: String = "user-default",
    val entityType: String, // CUSTOMER, ORDER, ORDER_ITEM, MESSAGE
    val entityId: String,
    val operationType: String, // CREATE, UPDATE, DELETE, RESTORE, STATUS_CHANGE
    val version: Int = 1,
    val baseVersion: Int = 0,
    val changedFieldsJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING", // PENDING, IN_FLIGHT, SYNCED, FAILED
    val hlcTimestamp: String? = null,
    val logicalClock: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
