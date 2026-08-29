package com.devcraft.sync.engine

import com.devcraft.data.local.dao.OperationDao
import com.devcraft.data.local.entities.OperationEntity

class OperationLogManager(private val operationDao: OperationDao, private val deviceId: String) {
    suspend fun logOperation(
        entityType: String,
        entityId: String,
        operationType: String,
        changedFieldsJson: String
    ) {
        val operation = OperationEntity(
            deviceId = deviceId,
            entityType = entityType,
            entityId = entityId,
            operationType = operationType,
            changedFieldsJson = changedFieldsJson
        )
        operationDao.insertOperation(operation)
    }
}
