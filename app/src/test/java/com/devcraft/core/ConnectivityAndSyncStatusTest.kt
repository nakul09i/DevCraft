package com.devcraft.core

import org.junit.Assert.*
import org.junit.Test

/**
 * The pure parts of connectivity: capability interpretation and status labels.
 * Registering a real NetworkCallback needs a device.
 */
class ConnectivityAndSyncStatusTest {

    @Test
    fun nullCapabilitiesMeansOffline() {
        // No active network at all
        assertEquals(ConnectionState.OFFLINE, ConnectivityObserver.stateOf(null))
    }

    @Test
    fun syncStatusLabelsAreTheOnesTheUiShows() {
        assertEquals("ONLINE", SyncStatus.ONLINE.label)
        assertEquals("OFFLINE", SyncStatus.OFFLINE.label)
        assertEquals("SYNCING", SyncStatus.SYNCING.label)
        assertEquals("SYNC ERROR", SyncStatus.SYNC_ERROR.label)
    }

    @Test
    fun allFourStatesAreDistinctlyLabelled() {
        val labels = SyncStatus.entries.map { it.label }
        assertEquals(4, labels.size)
        assertEquals("labels must be unique", labels.size, labels.toSet().size)
    }

    @Test
    fun connectionStateHasExactlyTwoValues() {
        // Deliberately binary: "maybe online" is what breaks offline-first apps.
        assertEquals(2, ConnectionState.entries.size)
    }
}
