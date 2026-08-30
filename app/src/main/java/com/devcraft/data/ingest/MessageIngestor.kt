package com.devcraft.data.ingest

import androidx.room.withTransaction
import com.devcraft.data.local.database.DevCraftDatabase
import com.devcraft.data.local.entities.MessageEntity
import com.devcraft.data.local.entities.MessageStatus
import com.devcraft.parser.offline.DeterministicParser
import com.devcraft.sync.engine.OperationLogManager
import java.util.UUID

/**
 * The single entry point for inbound messages, whatever the channel.
 *
 * Extracted so the SMS receiver and the ViewModel share one pipeline: one
 * parser, one Room write, one operation-log entry. There is deliberately no
 * second parser and no per-channel branch in the parsing logic - the channel
 * only changes the `source` metadata.
 *
 * Fully offline: DeterministicParser makes no network call and neither does this.
 */
class MessageIngestor(
    private val db: DevCraftDatabase,
    private val deviceId: String,
) {
    private val messageDao = db.messageDao()
    private val operationLog = OperationLogManager(db.operationDao(), deviceId)

    /**
     * Parses and persists [text]. Returns the new messageId, or null if the text
     * was blank. Message and operation-log rows are written in one transaction so
     * a message can never exist without its journal entry.
     */
    suspend fun ingest(
        text: String,
        source: String,
        sender: String? = null,
        senderName: String? = null,
        /** Set false for deliberate manual re-entry of identical text. */
        deduplicate: Boolean = true,
    ): String? {
        if (text.isBlank()) return null

        if (deduplicate) {
            val since = System.currentTimeMillis() - DUPLICATE_WINDOW_MS
            if (messageDao.countRecentDuplicates(text, sender, since) > 0) return null
        }

        val parsed = DeterministicParser.parse(text)
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val entity = MessageEntity(
            messageId = messageId,
            source = source,
            sender = sender,
            // Prefer the real contact/sender; fall back to the parsed customer name.
            senderName = senderName ?: parsed.customer,
            originalText = text,
            receivedAt = now,
            status = MessageStatus.PARSED.name,
            confidence = parsed.confidence,
            needsClarification = parsed.needs_clarification,
            classification = parsed.classification.name,
            classificationScore = parsed.classification_score,
            fieldExtractionScore = parsed.field_extraction_score,
            dateResolutionScore = parsed.date_resolution_score,
            clarificationDecisionScore = parsed.clarification_decision_score,
            overallScore = parsed.overall_score,
            rawDateText = parsed.raw_date_text,
            resolvedDate = parsed.due_date,
            createdAt = now,
            updatedAt = now,
        )


        db.withTransaction {
            messageDao.insertMessage(entity)
            operationLog.logOperation(
                entityType = "MESSAGE",
                entityId = messageId,
                operationType = "CREATE",
                changedFieldsJson = "{\"source\": \"$source\", \"status\": \"PARSED\"}",
            )
        }

        return messageId
    }

    companion object {
        /**
         * 10 minutes. Long enough to absorb carrier re-delivery and double-taps,
         * short enough that a genuine repeat order later the same day still lands.
         */
        const val DUPLICATE_WINDOW_MS = 10 * 60 * 1000L
    }
}
