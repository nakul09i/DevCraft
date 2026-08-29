package com.devcraft.sync.conflict

import com.devcraft.data.local.dao.ConflictDao
import com.devcraft.data.local.entities.ConflictEntity

object DeterministicConflictResolver {
    suspend fun resolveAndLog(
        conflictDao: ConflictDao,
        entityId: String,
        entityType: String,
        field: String,
        localValue: String?,
        remoteValue: String?,
        localTimestamp: Long,
        remoteTimestamp: Long,
        localDeviceId: String,
        remoteDeviceId: String
    ): String? {
        val (winningValue, reason) = when {
            remoteTimestamp > localTimestamp -> Pair(remoteValue, "REMOTE_TIMESTAMP_HIGHER")
            localTimestamp > remoteTimestamp -> Pair(localValue, "LOCAL_TIMESTAMP_HIGHER")
            else -> {
                if (remoteDeviceId > localDeviceId) Pair(remoteValue, "DEVICE_ID_TIE_BREAKER_REMOTE")
                else Pair(localValue, "DEVICE_ID_TIE_BREAKER_LOCAL")
            }
        }

        val conflict = ConflictEntity(
            entityId = entityId,
            entityType = entityType,
            field = field,
            localValue = localValue,
            remoteValue = remoteValue,
            winningValue = winningValue,
            resolutionReason = reason
        )
        conflictDao.insertConflict(conflict)
        return winningValue
    }
}
