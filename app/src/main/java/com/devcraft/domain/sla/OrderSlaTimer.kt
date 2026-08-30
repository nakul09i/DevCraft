package com.devcraft.domain.sla

import com.devcraft.data.local.entities.OrderEntity
import java.util.Locale

enum class SlaState {
    ON_TIME,
    APPROACHING_DEADLINE,
    OVERDUE
}

data class SlaTimerSnapshot(
    val state: SlaState,
    val elapsedTimeText: String,
    val remainingTimeText: String,
    val isOverdue: Boolean,
    val overdueDurationText: String? = null
)

object OrderSlaTimer {

    fun computeSla(order: OrderEntity, nowMillis: Long = System.currentTimeMillis()): SlaTimerSnapshot {
        val createdAt = order.createdAt
        val targetMinutes = order.targetDurationMinutes.coerceAtLeast(5)
        val targetMillis = targetMinutes * 60 * 1000L

        val elapsedMillis = (nowMillis - createdAt).coerceAtLeast(0L)
        val remainingMillis = targetMillis - elapsedMillis

        val elapsedSec = elapsedMillis / 1000
        val elapsedMin = elapsedSec / 60
        val elapsedSecRem = elapsedSec % 60
        val elapsedFormatted = String.format(Locale.US, "%02d:%02d", elapsedMin, elapsedSecRem)

        if (remainingMillis <= 0) {
            val overdueMillis = -remainingMillis
            val overdueSec = overdueMillis / 1000
            val overdueMin = overdueSec / 60
            val overdueSecRem = overdueSec % 60
            val overdueFormatted = String.format(Locale.US, "+%02d:%02d", overdueMin, overdueSecRem)

            return SlaTimerSnapshot(
                state = SlaState.OVERDUE,
                elapsedTimeText = elapsedFormatted,
                remainingTimeText = overdueFormatted,
                isOverdue = true,
                overdueDurationText = overdueFormatted
            )
        }

        val remSec = remainingMillis / 1000
        val remMin = remSec / 60
        val remSecRem = remSec % 60
        val remFormatted = String.format(Locale.US, "%02d:%02d", remMin, remSecRem)

        val state = if (remMin < 5) SlaState.APPROACHING_DEADLINE else SlaState.ON_TIME

        return SlaTimerSnapshot(
            state = state,
            elapsedTimeText = elapsedFormatted,
            remainingTimeText = remFormatted,
            isOverdue = false
        )
    }
}
