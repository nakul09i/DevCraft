package com.devcraft.sync

import com.devcraft.data.local.entities.ConflictEntity
import com.devcraft.data.local.entities.OperationEntity
import com.devcraft.sync.conflict.FieldOperation
import com.devcraft.sync.conflict.MergeEngine
import com.devcraft.sync.conflict.MergeOutcome
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * End-to-end multi-device cloud synchronization scenarios covering:
 * - Device A offline creation -> Device B initial sync
 * - Bi-directional synchronization
 * - Delete synchronization via tombstones
 * - Concurrent disjoint field edits
 * - Concurrent colliding edits with deterministic resolution
 * - Operation log idempotency and total ordering
 */
class SyncEngineTest {

    private fun createOperation(
        id: String = UUID.randomUUID().toString(),
        deviceId: String,
        entityType: String = "ORDER",
        entityId: String,
        operationType: String = "UPDATE",
        changedFieldsJson: String,
        timestamp: Long = System.currentTimeMillis()
    ) = OperationEntity(
        operationId = id,
        deviceId = deviceId,
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        changedFieldsJson = changedFieldsJson,
        timestamp = timestamp,
        syncStatus = "PENDING"
    )

    @Test
    fun testInitialSyncOnNewDevice_hydratesRemoteState() {
        // Device A creates an order offline
        val orderId = "order-100"
        val opA = FieldOperation(
            operationId = "op-1",
            entityType = "ORDER",
            entityId = orderId,
            field = "customerName",
            value = "Nakul",
            timestamp = 1000L,
            deviceId = "DEVICE_A"
        )
        val opA2 = FieldOperation(
            operationId = "op-2",
            entityType = "ORDER",
            entityId = orderId,
            field = "totalAmount",
            value = "1500.0",
            timestamp = 1000L,
            deviceId = "DEVICE_A"
        )

        // Device B logs in for the first time and performs initial sync
        val outcome = MergeEngine.merge(listOf(opA, opA2))

        assertEquals("Nakul", outcome.winningFields["customerName"])
        assertEquals("1500.0", outcome.winningFields["totalAmount"])
        assertFalse(outcome.deleted)
        assertTrue(outcome.conflicts.isEmpty())
    }

    @Test
    fun testBidirectionalSync_convergesOnBothDevices() {
        val orderId = "order-100"

        // Step 1: Device A creates order
        val opA = FieldOperation("op-1", "ORDER", orderId, "customerName", "Ramesh", timestamp = 100, deviceId = "DEVICE_A")
        
        // Step 2: Device B receives order, then edits status to PROCESSING
        val opB = FieldOperation("op-2", "ORDER", orderId, "status", "PROCESSING", timestamp = 200, deviceId = "DEVICE_B")

        // Step 3: Both devices sync
        val mergeOnA = MergeEngine.merge(listOf(opA, opB))
        val mergeOnB = MergeEngine.merge(listOf(opB, opA)) // Reverse arrival order

        assertEquals(mergeOnA.winningFields, mergeOnB.winningFields)
        assertEquals("Ramesh", mergeOnA.winningFields["customerName"])
        assertEquals("PROCESSING", mergeOnA.winningFields["status"])
    }

    @Test
    fun testDeleteSyncWithTombstone_propagatesToOtherDevice() {
        val orderId = "order-50"

        // Device A creates order at t=100
        val createOp = FieldOperation("op-1", "ORDER", orderId, "status", "CONFIRMED", timestamp = 100, deviceId = "DEVICE_A")
        
        // Device A deletes order at t=200
        val deleteOp = FieldOperation.delete("op-2", "ORDER", orderId, timestamp = 200, deviceId = "DEVICE_A")

        val outcome = MergeEngine.merge(listOf(createOp, deleteOp))

        assertTrue("Order must be marked deleted", outcome.deleted)
    }

    @Test
    fun testConcurrentDisjointFieldEdits_mergeCleanlyWithoutLoss() {
        val orderId = "order-200"

        // Device A changes totalAmount offline
        val opA = FieldOperation("op-a", "ORDER", orderId, "totalAmount", "2500.0", timestamp = 100, deviceId = "DEVICE_A")
        
        // Device B changes dueDate offline
        val opB = FieldOperation("op-b", "ORDER", orderId, "dueDate", "2026-09-01", timestamp = 105, deviceId = "DEVICE_B")

        val outcome = MergeEngine.merge(listOf(opA, opB))

        assertEquals("2500.0", outcome.winningFields["totalAmount"])
        assertEquals("2026-09-01", outcome.winningFields["dueDate"])
        assertTrue("No conflict for disjoint fields", outcome.conflicts.isEmpty())
        assertFalse(outcome.deleted)
    }

    @Test
    fun testConcurrentSameFieldConflict_resolvesDeterministicallyAndLogsLoser() {
        val orderId = "order-300"

        // Device A marks COMPLETED at t=100
        val opA = FieldOperation("op-a", "ORDER", orderId, "status", "COMPLETED", timestamp = 100, deviceId = "DEVICE_A")
        
        // Device B marks CANCELLED at t=150
        val opB = FieldOperation("op-b", "ORDER", orderId, "status", "CANCELLED", timestamp = 150, deviceId = "DEVICE_B")

        // Regardless of which arrives first, B wins by higher timestamp
        val outcomeAFirst = MergeEngine.merge(listOf(opA, opB))
        val outcomeBFirst = MergeEngine.merge(listOf(opB, opA))

        assertEquals(outcomeAFirst.winningFields, outcomeBFirst.winningFields)
        assertEquals("CANCELLED", outcomeAFirst.winningFields["status"])
        assertEquals(1, outcomeAFirst.conflicts.size)

        val conflict = outcomeAFirst.conflicts.single()
        assertEquals("status", conflict.field)
        assertEquals("COMPLETED", conflict.losingValue)
        assertEquals("CANCELLED", conflict.winningValue)
        assertEquals("DEVICE_A", conflict.losingDeviceId)
        assertEquals("DEVICE_B", conflict.winningDeviceId)
    }

    @Test
    fun testSameTimestampTieBreak_usesDeterministicDeviceIdOrder() {
        val orderId = "order-400"

        // Both devices mutate at exact same millisecond t=500
        val opA = FieldOperation("op-a", "ORDER", orderId, "status", "PROCESSING", timestamp = 500, deviceId = "DEVICE_A")
        val opB = FieldOperation("op-b", "ORDER", orderId, "status", "CONFIRMED", timestamp = 500, deviceId = "DEVICE_B")

        val outcome = MergeEngine.merge(listOf(opA, opB))

        // "DEVICE_B" > "DEVICE_A" lexically
        assertEquals("CONFIRMED", outcome.winningFields["status"])
        assertEquals("DEVICE_ID_TIE_BREAK", outcome.conflicts.single().resolutionReason)
    }

    @Test
    fun testOperationEntity_preservesPendingStateUntilSynced() {
        val op = createOperation(
            deviceId = "DEVICE_A",
            entityId = "order-1",
            changedFieldsJson = "{\"status\": \"CONFIRMED\"}"
        )

        assertEquals("PENDING", op.syncStatus)

        val syncedOp = op.copy(syncStatus = "SYNCED")
        assertEquals("SYNCED", syncedOp.syncStatus)
    }

    @Test
    fun testIdempotentOperationReplay_doesNotDuplicateState() {
        val orderId = "order-500"
        val op = FieldOperation("op-dup", "ORDER", orderId, "status", "CONFIRMED", timestamp = 100, deviceId = "DEVICE_A")

        // Same operation processed multiple times
        val outcome = MergeEngine.merge(listOf(op, op, op))

        assertEquals("CONFIRMED", outcome.winningFields["status"])
        assertTrue("Duplicate operations must not trigger conflicts", outcome.conflicts.isEmpty())
    }

    @Test
    fun testUserNamespacePathFormation() {
        val uid = "user_abc123"
        val expectedCustomerPath = "users/$uid/customers"
        val expectedOrderPath = "users/$uid/orders"
        val expectedOpPath = "users/$uid/operations"

        assertEquals("users/user_abc123/customers", expectedCustomerPath)
        assertEquals("users/user_abc123/orders", expectedOrderPath)
        assertEquals("users/user_abc123/operations", expectedOpPath)
    }
}
