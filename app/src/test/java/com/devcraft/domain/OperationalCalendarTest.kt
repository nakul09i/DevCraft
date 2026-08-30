package com.devcraft.domain

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class OperationalCalendarTest {

    private fun millisOf(iso: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$iso 12:00")!!.time

    @Test
    fun formatsIsoDate() {
        assertEquals("2026-09-05", OperationalCalendar.isoDate(millisOf("2026-09-05")))
    }

    @Test
    fun weekWindowStartsMondayAndEndsSunday() {
        // 2026-09-05 is a Saturday
        val (start, end) = OperationalCalendar.weekWindow(millisOf("2026-09-05"))
        assertEquals("2026-08-31", start) // Monday
        assertEquals("2026-09-06", end)   // Sunday
    }

    @Test
    fun weekWindowIsStableAcrossEveryDayOfThatWeek() {
        val expected = "2026-08-31" to "2026-09-06"
        for (day in 31..31) {
            assertEquals(expected, OperationalCalendar.weekWindow(millisOf("2026-08-$day")))
        }
        for (day in 1..6) {
            val iso = "2026-09-%02d".format(day)
            assertEquals("failed for $iso", expected, OperationalCalendar.weekWindow(millisOf(iso)))
        }
    }

    @Test
    fun sundayBelongsToTheWeekThatJustEnded() {
        // Monday-start weeks: Sunday is the last day, not the first
        val (start, end) = OperationalCalendar.weekWindow(millisOf("2026-09-06"))
        assertEquals("2026-08-31", start)
        assertEquals("2026-09-06", end)
    }

    @Test
    fun mondayIsItsOwnWeekStart() {
        val (start, _) = OperationalCalendar.weekWindow(millisOf("2026-08-31"))
        assertEquals("2026-08-31", start)
    }

    @Test
    fun weekWindowSpansMonthAndYearBoundaries() {
        // 2027-01-01 is a Friday, so its week starts in December 2026
        val (start, end) = OperationalCalendar.weekWindow(millisOf("2027-01-01"))
        assertEquals("2026-12-28", start)
        assertEquals("2027-01-03", end)
    }

    @Test
    fun isoDatesCompareChronologically() {
        // The operational SQL relies on this, so assert it explicitly
        assertTrue("2026-08-31" < "2026-09-01")
        assertTrue("2026-09-05" < "2026-09-06")
        assertTrue("2026-12-31" < "2027-01-01")
    }
}
