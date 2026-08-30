package com.devcraft.sync.version

import com.devcraft.data.local.entities.OperationEntity
import com.devcraft.data.local.entities.OrderEntity

data class VersionRecord(
    val version: Int,
    val summary: String,
    val authorDevice: String,
    val timestamp: Long,
    val operationType: String
)

object VersionHistoryManager {

    fun generateCommitMessage(
        oldOrder: OrderEntity?,
        newOrder: OrderEntity,
        operationType: String
    ): String {
        if (oldOrder == null || operationType == "CREATE") {
            return "v${newOrder.version} Created via ${newOrder.source}"
        }

        if (operationType == "DELETE") {
            return "v${newOrder.version} Order soft-deleted (Tombstone)"
        }

        if (operationType == "RESTORE") {
            return "v${newOrder.version} Restored previous version"
        }

        val diffs = mutableListOf<String>()

        if (oldOrder.customerName != newOrder.customerName) {
            diffs.add("Customer: '${oldOrder.customerName ?: "None"}' → '${newOrder.customerName}'")
        }
        if (oldOrder.totalAmount != newOrder.totalAmount) {
            diffs.add("Amount: ₹${oldOrder.totalAmount ?: 0.0} → ₹${newOrder.totalAmount ?: 0.0}")
        }
        if (oldOrder.dueDate != newOrder.dueDate) {
            diffs.add("Due Date: '${oldOrder.dueDate ?: "None"}' → '${newOrder.dueDate}'")
        }
        if (oldOrder.status != newOrder.status) {
            diffs.add("Status: ${oldOrder.status} → ${newOrder.status}")
        }
        if (oldOrder.formattedAddress != newOrder.formattedAddress) {
            diffs.add("Address updated")
        }

        return if (diffs.isEmpty()) {
            "v${newOrder.version} Updated details"
        } else {
            "v${newOrder.version} ${diffs.joinToString(", ")}"
        }
    }

    fun buildHistoryTimeline(
        currentOrder: OrderEntity,
        operations: List<OperationEntity>
    ): List<VersionRecord> {
        val records = mutableListOf<VersionRecord>()

        // Add current state
        records.add(
            VersionRecord(
                version = currentOrder.version,
                summary = "v${currentOrder.version} Current State (${currentOrder.status})",
                authorDevice = currentOrder.lastModifiedBy,
                timestamp = currentOrder.updatedAt,
                operationType = "STATUS_CHANGE"
            )
        )

        // Synthesize operations into timeline
        operations
            .filter { it.entityId == currentOrder.orderId }
            .sortedByDescending { it.version }
            .forEach { op ->
                if (op.version < currentOrder.version) {
                    records.add(
                        VersionRecord(
                            version = op.version,
                            summary = "v${op.version} ${op.operationType} by ${op.deviceId}",
                            authorDevice = op.deviceId,
                            timestamp = op.timestamp,
                            operationType = op.operationType
                        )
                    )
                }
            }

        if (records.none { it.version == 1 }) {
            records.add(
                VersionRecord(
                    version = 1,
                    summary = "v1 Created via ${currentOrder.source}",
                    authorDevice = currentOrder.deviceId,
                    timestamp = currentOrder.createdAt,
                    operationType = "CREATE"
                )
            )
        }

        return records.distinctBy { it.version }
    }
}
