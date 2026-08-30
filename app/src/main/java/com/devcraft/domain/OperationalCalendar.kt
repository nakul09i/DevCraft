package com.devcraft.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Date windows for the operational queries. Order due dates are stored as
 * ISO-8601 `yyyy-MM-dd` text, which sorts and compares lexicographically, so
 * every "due today / overdue / this week" question is a plain SQL comparison.
 *
 * Millis are passed in rather than read from the clock so this is unit-testable.
 */
object OperationalCalendar {

    private fun formatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun isoDate(millis: Long): String = formatter().format(java.util.Date(millis))

    fun today(nowMillis: Long = System.currentTimeMillis()): String = isoDate(nowMillis)

    /**
     * Monday-to-Sunday window containing [nowMillis], as inclusive ISO bounds.
     * Week start is pinned to Monday rather than taken from the locale, so
     * "committed capacity this week" means the same thing on every device.
     */
    fun weekWindow(nowMillis: Long = System.currentTimeMillis()): Pair<String, String> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        // Calendar: SUNDAY=1..SATURDAY=7. Normalise to Monday=0..Sunday=6.
        val mondayOffset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -mondayOffset)
        val start = isoDate(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        return start to isoDate(cal.timeInMillis)
    }
}
