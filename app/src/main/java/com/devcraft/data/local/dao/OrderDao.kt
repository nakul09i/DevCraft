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

/** Outstanding balance per customer, for the "who owes money" query. */
data class CustomerBalance(
    val customerName: String,
    val outstanding: Double,
    val openOrders: Int,
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

    // --- Operational queries. All local SQL: no network on any of these paths. ---
    // dueDate is ISO-8601 text, so lexicographic comparison is chronological.

    /** "What is due today?" */
    @Transaction
    @Query(
        "SELECT * FROM orders WHERE dueDate = :todayIso " +
            "AND status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY createdAt DESC"
    )
    fun getDueOn(todayIso: String): Flow<List<OrderWithItems>>

    /** "What is overdue?" */
    @Transaction
    @Query(
        "SELECT * FROM orders WHERE dueDate IS NOT NULL AND dueDate != '' " +
            "AND dueDate < :todayIso AND status NOT IN ('COMPLETED', 'CANCELLED') " +
            "ORDER BY dueDate ASC"
    )
    fun getOverdue(todayIso: String): Flow<List<OrderWithItems>>

    /**
     * "How much is outstanding?" Outstanding means the value of orders not yet
     * completed or cancelled; there is no separate payments table, so this is a
     * commitment total rather than an accounts-receivable balance.
     */
    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM orders WHERE status NOT IN ('COMPLETED', 'CANCELLED')")
    fun getOutstandingTotal(): Flow<Double>

    /** "Which customers owe money?" */
    @Query(
        "SELECT customerName AS customerName, COALESCE(SUM(totalAmount), 0) AS outstanding, " +
            "COUNT(*) AS openOrders FROM orders " +
            "WHERE status NOT IN ('COMPLETED', 'CANCELLED') AND customerName IS NOT NULL " +
            "GROUP BY customerName HAVING outstanding > 0 ORDER BY outstanding DESC"
    )
    fun getCustomerBalances(): Flow<List<CustomerBalance>>

    /**
     * "What did this customer order last time?" and, via its items,
     * "what specifications were used previously?"
     */
    @Transaction
    @Query(
        "SELECT * FROM orders WHERE customerName = :name COLLATE NOCASE " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLastOrderForCustomer(name: String): OrderWithItems?

    /** Full history for a customer, newest first. */
    @Transaction
    @Query(
        "SELECT * FROM orders WHERE customerName = :name COLLATE NOCASE ORDER BY createdAt DESC"
    )
    fun getOrdersForCustomer(name: String): Flow<List<OrderWithItems>>

    /** "What is committed capacity this week?" */
    @Query(
        "SELECT COALESCE(SUM(totalAmount), 0) FROM orders " +
            "WHERE dueDate BETWEEN :startIso AND :endIso AND status NOT IN ('COMPLETED', 'CANCELLED')"
    )
    fun getCommittedValueBetween(startIso: String, endIso: String): Flow<Double>

    @Query(
        "SELECT COUNT(*) FROM orders " +
            "WHERE dueDate BETWEEN :startIso AND :endIso AND status NOT IN ('COMPLETED', 'CANCELLED')"
    )
    fun getCommittedCountBetween(startIso: String, endIso: String): Flow<Int>

    /** Open orders that still have a due date, for re-arming alarms after reboot. */
    @Query(
        "SELECT * FROM orders WHERE dueDate IS NOT NULL AND dueDate != '' " +
            "AND status NOT IN ('COMPLETED', 'CANCELLED')"
    )
    suspend fun getOrdersAwaitingDueDate(): List<OrderEntity>

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
