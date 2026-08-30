package com.devcraft.data.local.dao

import androidx.room.*
import com.devcraft.data.local.entities.OperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationDao {
    @Query("SELECT * FROM operations WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingOperations(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingOperationsList(): List<OperationEntity>

    @Query("SELECT COUNT(*) FROM operations WHERE syncStatus = 'PENDING'")
    fun getPendingOperationsCountFlow(): Flow<Int>

    @Query("SELECT * FROM operations ORDER BY timestamp DESC")
    fun getAllOperations(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE entityId = :entityId ORDER BY timestamp ASC")
    suspend fun getOperationsForEntity(entityId: String): List<OperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: OperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperations(operations: List<OperationEntity>)

    @Query("UPDATE operations SET syncStatus = :status WHERE operationId = :id")
    suspend fun updateOperationStatus(id: String, status: String)

    @Query("UPDATE operations SET syncStatus = 'SYNCED' WHERE operationId IN (:ids)")
    suspend fun markOperationsSynced(ids: List<String>)
}

