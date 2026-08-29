package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey val conflictId: String = UUID.randomUUID().toString(),
    val entityId: String,
    val entityType: String,
    val field: String,
    val localValue: String?,
    val remoteValue: String?,
    val winningValue: String?,
    val resolutionReason: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = System.currentTimeMillis()
)
