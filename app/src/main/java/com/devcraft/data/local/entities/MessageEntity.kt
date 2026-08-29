package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageSource {
    MANUAL,
    WHATSAPP_SHARE,
    SMS,
    NOTIFICATION
}

enum class MessageStatus {
    RECEIVED,     // Raw message saved, not yet parsed
    PARSED,       // Deterministic parser executed, structured preview ready
    REVIEWED,     // User edited/reviewed parsed fields
    CONVERTED,    // Order and OrderItems generated and saved to Room
    ARCHIVED,     // User archived the message
    ERROR         // Parser or validation error
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String = UUID.randomUUID().toString(),
    val source: String = MessageSource.MANUAL.name,
    val sender: String? = null,              // Phone number or handle
    val senderName: String? = null,          // Contact display name
    val originalText: String,                 // Immutable preserved raw text
    val receivedAt: Long = System.currentTimeMillis(),
    val status: String = MessageStatus.RECEIVED.name,
    val confidence: Float = 0.0f,
    val parsedOrderId: String? = null,       // FK link to OrderEntity once converted
    val needsClarification: Boolean = false,
    val parseError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
