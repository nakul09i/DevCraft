package com.devcraft.data.local.dao

import androidx.room.*
import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.data.local.entities.OrderItemEntity
import kotlinx.coroutines.flow.Flow

data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "orderId",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :id LIMIT 1")
    suspend fun getOrderWithItemsById(id: String): OrderWithItems?

    /** Reactive variant: OrderDetailScreen must reflect status edits immediately. */
    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :id LIMIT 1")
    fun getOrderWithItemsByIdFlow(id: String): Flow<OrderWithItems?>

    @Transaction
    @Query("SELECT * FROM orders WHERE rawMessage LIKE '%' || :query || '%' OR customerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchOrders(query: String): Flow<List<OrderWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("DELETE FROM orders WHERE orderId = :id")
    suspend fun deleteOrderById(id: String)

    @Query("DELETE FROM order_items WHERE orderId = :id")
    suspend fun deleteOrderItemsByOrderId(id: String)

    @Transaction
    suspend fun deleteOrderComplete(id: String) {
        deleteOrderItemsByOrderId(id)
        deleteOrderById(id)
    }
}
