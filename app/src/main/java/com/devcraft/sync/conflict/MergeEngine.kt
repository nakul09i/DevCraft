package com.devcraft.sync.conflict

/**
 * A single field-level change from some device, as replayed from an operation log.
 *
 * [isDelete] marks a whole-entity delete; it competes against field updates
 * through the same ordering rules.
 */
data class FieldOperation(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val field: String,
    val value: String?,
    val timestamp: Long,
    val deviceId: String,
    val isDelete: Boolean = false,
) {
    companion object {
        /** Pseudo-field a delete competes on. */
        const val DELETE_FIELD = "__deleted__"

        fun delete(
            operationId: String,
            entityType: String,
            entityId: String,
            timestamp: Long,
            deviceId: String,
        ) = FieldOperation(
            operationId = operationId,
            entityType = entityType,
            entityId = entityId,
            field = DELETE_FIELD,
            value = null,
            timestamp = timestamp,
            deviceId = deviceId,
            isDelete = true,
        )
    }
}

/** A losing value, preserved so it is never silently discarded. */
data class ConflictRecord(
    val entityId: String,
    val entityType: String,
    val field: String,
    val losingValue: String?,
    val winningValue: String?,
    val losingDeviceId: String,
    val winningDeviceId: String,
    val resolutionReason: String,
)

data class MergeOutcome(
    /** Winning value per field. Excludes the delete pseudo-field. */
    val winningFields: Map<String, String?>,
    /** Every losing operation, in deterministic order. */
    val conflicts: List<ConflictRecord>,
    /** True when a delete won over all competing field updates. */
    val deleted: Boolean,
)

/**
 * Deterministic field-level merge.
 *
 * Convergence guarantee: operations are ordered by a *total* order -
 * timestamp, then deviceId, then operationId. Because operationId is a unique
 * UUID, no two distinct operations ever compare equal, so any permutation of
 * the same operation set produces the identical winner. That is what makes the
 * final state independent of the order devices reconnect in.
 *
 * ponytail: ordering on the wall-clock `timestamp` column that OperationEntity
 * already has, rather than a true Hybrid Logical Clock. This converges
 * deterministically, but it cannot detect causality, so a device with a skewed
 * clock can win a race it did not causally win. Upgrade path: add
 * hlcTimestamp/logicalClock to OperationEntity and order on those first - the
 * comparator below is the only place that needs to change.
 */
object MergeEngine {

    /**
     * Highest wins. Compares timestamp, then deviceId lexically, then
     * operationId as the final stable tie-break.
     */
    private val PRECEDENCE: Comparator<FieldOperation> =
        compareBy<FieldOperation> { it.timestamp }
            .thenBy { it.deviceId }
            .thenBy { it.operationId }

    fun merge(operations: List<FieldOperation>): MergeOutcome {
        if (operations.isEmpty()) {
            return MergeOutcome(emptyMap(), emptyList(), deleted = false)
        }

        val winningFields = LinkedHashMap<String, String?>()
        val conflicts = mutableListOf<ConflictRecord>()

        // Deduplicate retransmitted operations by unique operationId for idempotency
        val distinctOps = operations.distinctBy { it.operationId }
        val byField = distinctOps.groupBy { it.field }


        for (field in byField.keys.sorted()) {
            val ordered = byField.getValue(field).sortedWith(PRECEDENCE)
            val winner = ordered.last()

            if (field != FieldOperation.DELETE_FIELD) {
                winningFields[field] = winner.value
            }

            // Everything that lost this field is recorded, never dropped.
            for (loser in ordered.dropLast(1)) {
                conflicts += ConflictRecord(
                    entityId = winner.entityId,
                    entityType = winner.entityType,
                    field = field,
                    losingValue = loser.value,
                    winningValue = winner.value,
                    losingDeviceId = loser.deviceId,
                    winningDeviceId = winner.deviceId,
                    resolutionReason = reasonFor(winner, loser),
                )
            }
        }

        val deleteOp = byField[FieldOperation.DELETE_FIELD]?.maxWithOrNull(PRECEDENCE)
        var deleted = false

        if (deleteOp != null) {
            // Delete competes against the latest surviving field update.
            val latestUpdate = operations
                .filter { it.field != FieldOperation.DELETE_FIELD }
                .maxWithOrNull(PRECEDENCE)

            deleted = latestUpdate == null || PRECEDENCE.compare(deleteOp, latestUpdate) > 0

            if (deleted && latestUpdate != null) {
                // The row goes away, but the edit someone made must stay visible.
                conflicts += ConflictRecord(
                    entityId = deleteOp.entityId,
                    entityType = deleteOp.entityType,
                    field = latestUpdate.field,
                    losingValue = latestUpdate.value,
                    winningValue = null,
                    losingDeviceId = latestUpdate.deviceId,
                    winningDeviceId = deleteOp.deviceId,
                    resolutionReason = "DELETE_WON_OVER_UPDATE",
                )
            } else if (!deleted) {
                conflicts += ConflictRecord(
                    entityId = deleteOp.entityId,
                    entityType = deleteOp.entityType,
                    field = FieldOperation.DELETE_FIELD,
                    losingValue = "DELETE",
                    winningValue = latestUpdate?.value,
                    losingDeviceId = deleteOp.deviceId,
                    winningDeviceId = latestUpdate?.deviceId ?: "",
                    resolutionReason = "UPDATE_WON_OVER_DELETE",
                )
            }
        }

        // Sort so the conflict list itself is order-independent too.
        val stableConflicts = conflicts.sortedWith(
            compareBy({ it.field }, { it.losingDeviceId }, { it.losingValue ?: "" })
        )

        return MergeOutcome(winningFields, stableConflicts, deleted)
    }

    private fun reasonFor(winner: FieldOperation, loser: FieldOperation): String = when {
        winner.timestamp != loser.timestamp -> "HIGHER_TIMESTAMP"
        winner.deviceId != loser.deviceId -> "DEVICE_ID_TIE_BREAK"
        else -> "OPERATION_ID_TIE_BREAK"
    }
}
