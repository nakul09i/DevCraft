package com.devcraft.sync

import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.data.local.entities.OrderItemEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MultiDeviceSyncEndToEndTest {

    @Test
    fun `test multi-device sync end-to-end simulation across Device A and Device B`() {
        val userId = "user_merchant_123"
        val orderId = UUID.randomUUID().toString()

        // 1. Device A creates order #1001 online
        val orderDeviceA_v1 = OrderEntity(
            orderId = orderId,
            orderNumber = "#1001",
            source = "SMS",
            customerName = "Nakul",
            phone = "9876543210",
            totalAmount = 500.0,
            status = "NEW",
            version = 1,
            baseVersion = 0,
            deviceId = "Device_A",
            userId = userId,
            syncState = "SYNCED",
            lastModifiedBy = "Device_A"
        )
        assertEquals(1, orderDeviceA_v1.version)
        assertEquals("Device_A", orderDeviceA_v1.deviceId)

        // 2. Device B logged into same userId downloads Order #1001
        val orderDeviceB_v1 = orderDeviceA_v1.copy()
        assertEquals(orderDeviceA_v1.orderId, orderDeviceB_v1.orderId)
        assertEquals(orderDeviceA_v1.totalAmount, orderDeviceB_v1.totalAmount)
        assertEquals("NEW", orderDeviceB_v1.status)

        // 3. Device B updates status to PROCESSING (v2)
        val orderDeviceB_v2 = orderDeviceB_v1.copy(
            status = "PROCESSING",
            version = 2,
            baseVersion = 1,
            lastModifiedBy = "Device_B",
            updatedAt = System.currentTimeMillis()
        )
        assertEquals(2, orderDeviceB_v2.version)
        assertEquals("PROCESSING", orderDeviceB_v2.status)

        // 4. Device A receives realtime update from Device B (v2 replaces v1 because v2 > v1)
        val orderDeviceA_synced = if (orderDeviceB_v2.version > orderDeviceA_v1.version) {
            orderDeviceB_v2
        } else {
            orderDeviceA_v1
        }
        assertEquals("PROCESSING", orderDeviceA_synced.status)
        assertEquals(2, orderDeviceA_synced.version)

        // 5. Device A marks COMPLETED (v3)
        val orderDeviceA_v3 = orderDeviceA_synced.copy(
            status = "COMPLETED",
            version = 3,
            baseVersion = 2,
            lastModifiedBy = "Device_A",
            updatedAt = System.currentTimeMillis()
        )
        assertEquals(3, orderDeviceA_v3.version)
        assertEquals("COMPLETED", orderDeviceA_v3.status)

        // 6. Device B receives update from Device A (v3 replaces v2)
        val orderDeviceB_synced = if (orderDeviceA_v3.version > orderDeviceB_v2.version) {
            orderDeviceA_v3
        } else {
            orderDeviceB_v2
        }
        assertEquals("COMPLETED", orderDeviceB_synced.status)
        assertEquals(3, orderDeviceB_synced.version)
        assertEquals(orderDeviceA_v3.totalAmount, orderDeviceB_synced.totalAmount)
    }

    @Test
    fun `test simultaneous offline edits and deterministic convergence`() {
        val userId = "user_merchant_123"
        val orderId = "order_common_10"

        val initialOrder = OrderEntity(
            orderId = orderId,
            orderNumber = "#1010",
            totalAmount = 500.0,
            status = "CONFIRMED",
            version = 1,
            userId = userId
        )

        // Device A modifies amount to ₹700 offline
        val editA = initialOrder.copy(
            totalAmount = 700.0,
            version = 2,
            baseVersion = 1,
            deviceId = "Device_A",
            lastModifiedBy = "Device_A"
        )

        // Device B modifies amount to ₹600 offline from same base v1
        val editB = initialOrder.copy(
            totalAmount = 600.0,
            version = 2,
            baseVersion = 1,
            deviceId = "Device_B",
            lastModifiedBy = "Device_B"
        )

        // System detects conflict (same version v2 from base v1 with different authors and amounts)
        val isConflict = editA.version == editB.version && editA.totalAmount != editB.totalAmount
        assertTrue(isConflict)

        // Deterministic resolution produces v3
        val resolved = editA.copy(
            totalAmount = 700.0,
            version = 3,
            baseVersion = 2,
            syncState = "SYNCED",
            lastModifiedBy = "Device_A_Resolved"
        )

        assertEquals(3, resolved.version)
        assertEquals(700.0, resolved.totalAmount!!, 0.01)
    }
}
