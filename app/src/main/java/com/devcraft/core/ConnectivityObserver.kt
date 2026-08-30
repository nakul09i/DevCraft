package com.devcraft.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectionState { ONLINE, OFFLINE }

/**
 * Real device connectivity, replacing the "Simulate Online Mode" button.
 *
 * Requires NET_CAPABILITY_VALIDATED, not merely INTERNET: a captive portal or a
 * connected-but-dead Wi-Fi reports INTERNET while nothing can actually reach the
 * network. Treating that as ONLINE is exactly how an offline-first app ends up
 * hanging on a request.
 *
 * Nothing in the app gates on this. It is display state plus a future sync
 * trigger; the order workflow ignores it entirely.
 */
class ConnectivityObserver(context: Context) {

    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun current(): ConnectionState {
        val cm = manager ?: return ConnectionState.OFFLINE
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return stateOf(capabilities)
    }

    /** Emits on every transition. Cold; unregisters the callback when collection stops. */
    fun observe(): Flow<ConnectionState> = callbackFlow {
        val cm = manager
        if (cm == null) {
            trySend(ConnectionState.OFFLINE)
            awaitClose { }
            return@callbackFlow
        }

        // Re-read the active network rather than trusting the callback's argument:
        // onLost fires per-network, and another network may still be up.
        fun push() = trySend(current())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { push() }
            override fun onLost(network: Network) { push() }
            override fun onUnavailable() { push() }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) { push() }
        }

        push()
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    companion object {
        fun stateOf(capabilities: NetworkCapabilities?): ConnectionState {
            if (capabilities == null) return ConnectionState.OFFLINE
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return if (hasInternet && validated) ConnectionState.ONLINE else ConnectionState.OFFLINE
        }
    }
}

/**
 * What the UI shows. SYNCING and SYNC_ERROR are modelled but cannot occur yet -
 * there is no sync transport - so they are never emitted today. Documented
 * rather than displayed as if working.
 */
enum class SyncStatus { OFFLINE, ONLINE, SYNCING, SYNC_ERROR;

    val label: String
        get() = when (this) {
            OFFLINE -> "OFFLINE"
            ONLINE -> "ONLINE"
            SYNCING -> "SYNCING"
            SYNC_ERROR -> "SYNC ERROR"
        }
}
