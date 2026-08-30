package com.devcraft.sync.conflict

import org.junit.Assert.*
import org.junit.Test

/**
 * The three conflict scenarios from CLAUDE.md section 11, plus the property that
 * actually matters: convergence must not depend on the order operations arrive.
 *
 * Every scenario is checked against ALL permutations of its operation set, which
 * is the real proof that reconnection order is irrelevant.
 */
class MergeEngineTest {

    private fun op(
        id: String,
        field: String,
        value: String?,
        timestamp: Long,
        device: String,
    ) = FieldOperation(
        operationId = id,
        entityType = "ORDER",
        entityId = "order-1",
        field = field,
        value = value,
        timestamp = timestamp,
        deviceId = device,
    )

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) listOf(items)
        else items.flatMap { head ->
            permutations(items - head).map { listOf(head) + it }
        }

    /** Asserts every arrival order yields byte-identical merge output. */
    private fun assertConverges(ops: List<FieldOperation>): MergeOutcome {
        val results = permutations(ops).map { MergeEngine.merge(it) }
        val first = results.first()
        results.forEachIndexed { i, r ->
            assertEquals("permutation $i diverged on winning fields", first.winningFields, r.winningFields)
            assertEquals("permutation $i diverged on deleted flag", first.deleted, r.deleted)
            assertEquals("permutation $i diverged on conflicts", first.conflicts, r.conflicts)
        }
        assertTrue("expected multiple permutations to be checked", results.size > 1)
        return first
    }

    // --- Scenario 1: disjoint fields merge cleanly ---

    @Test
    fun scenario1_disjointFieldEditsMergeWithoutConflict() {
        val ops = listOf(
            op("op-a", "dueDate", "2026-09-05", timestamp = 100, device = "device-A"),
            op("op-b", "totalAmount", "4500.0", timestamp = 105, device = "device-B"),
        )

        val outcome = assertConverges(ops)

        assertEquals("2026-09-05", outcome.winningFields["dueDate"])
        assertEquals("4500.0", outcome.winningFields["totalAmount"])
        assertEquals("disjoint edits must not produce a conflict", emptyList<ConflictRecord>(), outcome.conflicts)
        assertFalse(outcome.deleted)
    }

    // --- Scenario 2: same-field collision converges and is surfaced ---

    @Test
    fun scenario2_sameFieldHigherTimestampWinsAndLoserIsLogged() {
        val ops = listOf(
            op("op-a", "status", "COMPLETED", timestamp = 100, device = "device-A"),
            op("op-b", "status", "CANCELLED", timestamp = 105, device = "device-B"),
        )

        val outcome = assertConverges(ops)

        assertEquals("CANCELLED", outcome.winningFields["status"])
        assertEquals(1, outcome.conflicts.size)
        val conflict = outcome.conflicts.single()
        assertEquals("status", conflict.field)
        assertEquals("COMPLETED", conflict.losingValue)
        assertEquals("CANCELLED", conflict.winningValue)
        assertEquals("HIGHER_TIMESTAMP", conflict.resolutionReason)
    }

    @Test
    fun scenario2_identicalTimestampsBreakTieOnDeviceId() {
        val ops = listOf(
            op("op-a", "status", "COMPLETED", timestamp = 100, device = "device-A"),
            op("op-b", "status", "CANCELLED", timestamp = 100, device = "device-B"),
        )

        val outcome = assertConverges(ops)

        // device-B > device-A lexically, so B wins
        assertEquals("CANCELLED", outcome.winningFields["status"])
        assertEquals("DEVICE_ID_TIE_BREAK", outcome.conflicts.single().resolutionReason)
    }

    @Test
    fun sameDeviceAndTimestampStillConvergesViaOperationId() {
        // Pathological but must not be ambiguous: identical clock and device.
        val ops = listOf(
            op("op-aaa", "status", "COMPLETED", timestamp = 100, device = "device-A"),
            op("op-bbb", "status", "CANCELLED", timestamp = 100, device = "device-A"),
        )

        val outcome = assertConverges(ops)

        assertEquals("CANCELLED", outcome.winningFields["status"])
        assertEquals("OPERATION_ID_TIE_BREAK", outcome.conflicts.single().resolutionReason)
    }

    // --- Scenario 3: delete vs update ---

    @Test
    fun scenario3_laterDeleteWinsButLosingUpdateStaysVisible() {
        val ops = listOf(
            op("op-a", "status", "PROCESSING", timestamp = 100, device = "device-A"),
            FieldOperation.delete("op-b", "ORDER", "order-1", timestamp = 200, deviceId = "device-B"),
        )

        val outcome = assertConverges(ops)

        assertTrue("later delete must win", outcome.deleted)
        assertTrue(
            "the losing edit must still be surfaced",
            outcome.conflicts.any {
                it.resolutionReason == "DELETE_WON_OVER_UPDATE" && it.losingValue == "PROCESSING"
            },
        )
    }

    @Test
    fun scenario3_laterUpdateBeatsEarlierDeleteAndDeleteIntentIsSurfaced() {
        val ops = listOf(
            FieldOperation.delete("op-a", "ORDER", "order-1", timestamp = 100, deviceId = "device-A"),
            op("op-b", "status", "COMPLETED", timestamp = 200, device = "device-B"),
        )

        val outcome = assertConverges(ops)

        assertFalse("later update must survive the earlier delete", outcome.deleted)
        assertEquals("COMPLETED", outcome.winningFields["status"])
        assertTrue(
            "the discarded delete intent must be surfaced",
            outcome.conflicts.any { it.resolutionReason == "UPDATE_WON_OVER_DELETE" },
        )
    }

    // --- general properties ---

    @Test
    fun noOperationIsEverSilentlyDiscarded() {
        val ops = listOf(
            op("op-a", "status", "A", timestamp = 100, device = "device-A"),
            op("op-b", "status", "B", timestamp = 101, device = "device-B"),
            op("op-c", "status", "C", timestamp = 102, device = "device-C"),
        )

        val outcome = assertConverges(ops)

        assertEquals("C", outcome.winningFields["status"])
        // 3 operations, 1 winner => exactly 2 recorded losers
        assertEquals(2, outcome.conflicts.size)
        assertEquals(setOf("A", "B"), outcome.conflicts.mapNotNull { it.losingValue }.toSet())
    }

    @Test
    fun mixedDisjointAndConflictingEditsResolveIndependently() {
        val ops = listOf(
            op("op-a", "dueDate", "2026-09-05", timestamp = 100, device = "device-A"),
            op("op-b", "status", "COMPLETED", timestamp = 100, device = "device-A"),
            op("op-c", "status", "CANCELLED", timestamp = 150, device = "device-B"),
        )

        val outcome = assertConverges(ops)

        assertEquals("2026-09-05", outcome.winningFields["dueDate"])
        assertEquals("CANCELLED", outcome.winningFields["status"])
        assertEquals("only the contested field conflicts", 1, outcome.conflicts.size)
        assertEquals("status", outcome.conflicts.single().field)
    }

    @Test
    fun singleOperationProducesNoConflict() {
        val outcome = MergeEngine.merge(
            listOf(op("op-a", "status", "CONFIRMED", timestamp = 100, device = "device-A"))
        )
        assertEquals("CONFIRMED", outcome.winningFields["status"])
        assertTrue(outcome.conflicts.isEmpty())
        assertFalse(outcome.deleted)
    }

    @Test
    fun emptyOperationListIsSafe() {
        val outcome = MergeEngine.merge(emptyList())
        assertTrue(outcome.winningFields.isEmpty())
        assertTrue(outcome.conflicts.isEmpty())
        assertFalse(outcome.deleted)
    }
}
