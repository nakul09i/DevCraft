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
 * Real device connectivity observer.
 * Emits ONLINE whenever the active network has internet capability.
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
            return if (hasInternet) ConnectionState.ONLINE else ConnectionState.OFFLINE
        }
    }
}

enum class SyncStatus { OFFLINE, ONLINE, SYNCING, SYNC_ERROR;

    val label: String
        get() = when (this) {
            OFFLINE -> "OFFLINE"
            ONLINE -> "ONLINE"
            SYNCING -> "SYNCING"
            SYNC_ERROR -> "SYNC ERROR"
        }
}
