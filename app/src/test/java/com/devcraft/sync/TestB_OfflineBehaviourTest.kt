package com.devcraft.sync

import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.sync.version.VersionHistoryManager
import org.junit.Assert.*
import org.junit.Test

class TestB_OfflineBehaviourTest {

    @Test
    fun `test offline order creation edit delete and version tracking`() {
        // 1. Create order offline
        val initialOrder = OrderEntity(
            orderId = "ord_b1",
            orderNumber = "#1001",
            source = "SMS",
            customerName = "Nakul",
            totalAmount = 500.0,
            version = 1,
            syncState = "PENDING"
        )
        assertEquals(1, initialOrder.version)
        assertEquals("PENDING", initialOrder.syncState)

        // 2. Edit order offline -> increments version
        val editedOrder = initialOrder.copy(
            totalAmount = 700.0,
            version = initialOrder.version + 1,
            baseVersion = initialOrder.version
        )
        assertEquals(2, editedOrder.version)
        assertEquals(1, editedOrder.baseVersion)
        assertEquals(700.0, editedOrder.totalAmount!!, 0.01)

        // 3. Diff summary
        val msg = VersionHistoryManager.generateCommitMessage(initialOrder, editedOrder, "UPDATE")
        assertTrue(msg.contains("v2"))
        assertTrue(msg.contains("700.0"))

        // 4. Soft delete offline (Tombstone)
        val deletedOrder = editedOrder.copy(
            isDeleted = true,
            version = editedOrder.version + 1,
            baseVersion = editedOrder.version
        )
        assertTrue(deletedOrder.isDeleted)
        assertEquals(3, deletedOrder.version)
    }
}
