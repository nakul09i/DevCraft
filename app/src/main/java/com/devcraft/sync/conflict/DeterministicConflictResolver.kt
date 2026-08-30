package com.devcraft.sync.conflict

import com.devcraft.data.local.dao.ConflictDao
import com.devcraft.data.local.entities.ConflictEntity

/**
 * Persistence wrapper around [MergeEngine].
 *
 * The merge itself is pure and lives in MergeEngine so it can be tested against
 * every permutation of an operation set. This layer only records the outcome, so
 * that every losing value becomes visible to the merchant in the Conflicts
 * screen instead of being dropped.
 */
object DeterministicConflictResolver {

    /**
     * Merges [operations] for one entity and writes every losing value to the
     * conflict log. Returns the merged outcome for the caller to apply to Room.
     */
    suspend fun mergeAndLog(
        conflictDao: ConflictDao,
        operations: List<FieldOperation>,
    ): MergeOutcome {
        val outcome = MergeEngine.merge(operations)

        for (conflict in outcome.conflicts) {
            conflictDao.insertConflict(
                ConflictEntity(
                    entityId = conflict.entityId,
                    entityType = conflict.entityType,
                    field = conflict.field,
                    // The existing columns predate the device-agnostic merge, so
                    // losing maps to localValue and winning to remoteValue.
                    localValue = conflict.losingValue,
                    remoteValue = conflict.winningValue,
                    winningValue = conflict.winningValue,
                    resolutionReason = buildString {
                        append(conflict.resolutionReason)
                        append(" (won: ").append(conflict.winningDeviceId)
                        append(", lost: ").append(conflict.losingDeviceId).append(')')
                    },
                )
            )
        }

        return outcome
    }
}
