package com.devcraft.data.local.dao

import androidx.room.*
import com.devcraft.data.local.entities.ConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {
    @Query("SELECT * FROM conflicts ORDER BY createdAt DESC")
    fun getAllConflicts(): Flow<List<ConflictEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: ConflictEntity)
}
