package com.devcraft.sla

import com.devcraft.data.local.entities.OrderEntity
import com.devcraft.domain.sla.OrderSlaTimer
import com.devcraft.domain.sla.SlaState
import org.junit.Assert.*
import org.junit.Test

class OrderSlaTimerTest {

    @Test
    fun `test SLA timer states across timestamps`() {
        val now = 1000000000000L
        val createdAt = now - (10 * 60 * 1000L) // 10 mins ago

        val order = OrderEntity(
            createdAt = createdAt,
            targetDurationMinutes = 30
        )

        // 10 mins elapsed out of 30 mins -> ON_TIME
        val snapshot1 = OrderSlaTimer.computeSla(order, now)
        assertEquals(SlaState.ON_TIME, snapshot1.state)
        assertEquals("10:00", snapshot1.elapsedTimeText)
        assertEquals("20:00", snapshot1.remainingTimeText)
        assertFalse(snapshot1.isOverdue)

        // 26 mins elapsed out of 30 mins -> APPROACHING_DEADLINE (< 5 mins)
        val nowApproaching = createdAt + (26 * 60 * 1000L)
        val snapshot2 = OrderSlaTimer.computeSla(order, nowApproaching)
        assertEquals(SlaState.APPROACHING_DEADLINE, snapshot2.state)
        assertEquals("26:00", snapshot2.elapsedTimeText)
        assertEquals("04:00", snapshot2.remainingTimeText)
        assertFalse(snapshot2.isOverdue)

        // 34 mins elapsed out of 30 mins -> OVERDUE
        val nowOverdue = createdAt + (34 * 60 * 1000L)
        val snapshot3 = OrderSlaTimer.computeSla(order, nowOverdue)
        assertEquals(SlaState.OVERDUE, snapshot3.state)
        assertEquals("34:00", snapshot3.elapsedTimeText)
        assertEquals("+04:00", snapshot3.remainingTimeText)
        assertTrue(snapshot3.isOverdue)
    }
}
