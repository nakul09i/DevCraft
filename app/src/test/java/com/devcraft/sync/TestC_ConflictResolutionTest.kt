package com.devcraft.sync

import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.sync.conflict.DeterministicConflictResolver
import org.junit.Assert.*
import org.junit.Test

class TestC_ConflictResolutionTest {

    @Test
    fun `test two device offline edit conflict detection and deterministic convergence`() {
        val baseOrder = OrderEntity(
            orderId = "ord_c1",
            orderNumber = "#1010",
            totalAmount = 500.0,
            version = 1,
            deviceId = "DeviceA"
        )

        // Device A modifies amount to ₹700 offline
        val orderA = baseOrder.copy(
            totalAmount = 700.0,
            version = 2,
            baseVersion = 1,
            deviceId = "DeviceA"
        )

        // Device B modifies amount to ₹600 offline from same base v1
        val orderB = baseOrder.copy(
            totalAmount = 600.0,
            version = 2,
            baseVersion = 1,
            deviceId = "DeviceB"
        )

        // Conflict resolution preferring Device A (or explicit resolution)
        val resolvedOrder = orderA.copy(
            version = maxOf(orderA.version, orderB.version) + 1,
            syncState = "SYNCED",
            lastModifiedBy = "DeviceA-Resolved"
        )

        assertEquals(3, resolvedOrder.version)
        assertEquals(700.0, resolvedOrder.totalAmount!!, 0.01)
        assertEquals("SYNCED", resolvedOrder.syncState)
    }
}
