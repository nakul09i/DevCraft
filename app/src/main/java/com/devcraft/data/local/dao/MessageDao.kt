package com.devcraft.data.local.dao

import androidx.room.*
import com.devcraft.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY receivedAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE messageId = :id LIMIT 1")
    fun getMessageByIdFlow(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE status = :status ORDER BY receivedAt DESC")
    fun getMessagesByStatus(status: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status IN ('RECEIVED', 'PARSED', 'REVIEWED') OR needsClarification = 1 ORDER BY receivedAt DESC")
    fun getReviewNeededMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE originalText LIKE '%' || :query || '%' OR senderName LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' ORDER BY receivedAt DESC")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status, updatedAt = :updatedAt WHERE messageId = :id")
    suspend fun updateMessageStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET parsedOrderId = :orderId, status = :status, updatedAt = :updatedAt WHERE messageId = :messageId")
    suspend fun linkParsedOrder(
        messageId: String,
        orderId: String,
        status: String = "CONVERTED",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM messages WHERE messageId = :id")
    suspend fun deleteMessageById(id: String)

    @Query("SELECT COUNT(*) FROM messages WHERE status IN ('RECEIVED', 'PARSED')")
    fun getUnreadMessageCountFlow(): Flow<Int>
}
