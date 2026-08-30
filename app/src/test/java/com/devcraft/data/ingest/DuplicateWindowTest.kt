package com.devcraft.data.ingest

import org.junit.Assert.*
import org.junit.Test

/**
 * The duplicate window is a policy decision, so it gets a test that fails if
 * someone changes it without thinking. The SQL itself needs an instrumented test.
 */
class DuplicateWindowTest {

    @Test
    fun windowIsTenMinutes() {
        assertEquals(10 * 60 * 1000L, MessageIngestor.DUPLICATE_WINDOW_MS)
    }

    @Test
    fun windowAbsorbsCarrierRedeliveryButNotALaterRepeatOrder() {
        val window = MessageIngestor.DUPLICATE_WINDOW_MS

        // A re-delivered SMS arrives within seconds - must be inside the window.
        assertTrue("30s re-delivery must be caught", 30_000L < window)

        // The same customer ordering the same thing hours later is a real order.
        assertTrue("2h later must NOT be caught", 2 * 60 * 60 * 1000L > window)

        // And an accidental double-share a minute later is still a duplicate.
        assertTrue("60s double-share must be caught", 60_000L < window)
    }
}
