package com.devcraft.data.local.dao

import androidx.room.*
import com.devcraft.data.local.entities.OperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationDao {
    @Query("SELECT * FROM operations WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingOperations(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations ORDER BY timestamp DESC")
    fun getAllOperations(): Flow<List<OperationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: OperationEntity)

    @Query("UPDATE operations SET syncStatus = :status WHERE operationId = :id")
    suspend fun updateOperationStatus(id: String, status: String)
}
