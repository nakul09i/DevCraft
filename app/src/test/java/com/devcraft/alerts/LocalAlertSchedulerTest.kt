package com.devcraft.alerts

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * triggerTimeFor is pure Java date math, so it is unit-testable without a
 * device. The AlarmManager calls around it are not.
 */
class LocalAlertSchedulerTest {

    private fun fieldsOf(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun resolvesDueDateToNineAmLocal() {
        val millis = LocalAlertScheduler.triggerTimeFor("2026-09-05")
        assertNotNull(millis)
        val cal = Calendar.getInstance().apply { timeInMillis = millis!! }
        assertEquals(LocalAlertScheduler.ALERT_HOUR, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(Triple(2026, 9, 5), fieldsOf(millis!!))
    }

    @Test
    fun rejectsBlankAndNull() {
        assertNull(LocalAlertScheduler.triggerTimeFor(null))
        assertNull(LocalAlertScheduler.triggerTimeFor(""))
        assertNull(LocalAlertScheduler.triggerTimeFor("   "))
    }

    @Test
    fun rejectsMalformedDates() {
        // Non-lenient parsing: these must not silently roll over into a real date
        assertNull(LocalAlertScheduler.triggerTimeFor("tomorrow"))
        assertNull(LocalAlertScheduler.triggerTimeFor("05-09-2026"))
        assertNull(LocalAlertScheduler.triggerTimeFor("2026-13-45"))
    }

    @Test
    fun requestCodeIsStableForTheSameOrder() {
        val id = "0f8b2c1a-1111-2222-3333-444455556666"
        assertEquals(LocalAlertScheduler.requestCodeFor(id), LocalAlertScheduler.requestCodeFor(id))
        assertNotEquals(LocalAlertScheduler.requestCodeFor(id), LocalAlertScheduler.requestCodeFor(id + "x"))
    }
}
