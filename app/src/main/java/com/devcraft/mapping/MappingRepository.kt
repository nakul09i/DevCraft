package com.devcraft.mapping

/** A resolved point on the map. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
}

data class ResolvedPlace(
    val point: GeoPoint,
    val formattedAddress: String?,
    val placeId: String?,
)

data class RouteSummary(
    val distanceMeters: Int,
    val durationSeconds: Int,
    /** Encoded polyline, if the provider returned one. */
    val geometry: String?,
)

/**
 * Every mapping outcome the UI must be able to render. Modelled explicitly
 * rather than as exceptions, because "no credentials" and "offline" are normal
 * states in an offline-first app, not errors to be thrown.
 */
sealed interface MappingResult<out T> {
    data class Success<T>(val value: T) : MappingResult<T>

    /** No MAPPLS_API_KEY was supplied at build time. */
    data object NotConfigured : MappingResult<Nothing>

    /** No usable network. Callers should fall back to cached coordinates. */
    data object Offline : MappingResult<Nothing>

    /** Provider reached but returned nothing for this input. */
    data object NoResult : MappingResult<Nothing>

    data class Failure(val message: String, val cause: Throwable? = null) : MappingResult<Nothing>
}

/**
 * Provider-agnostic mapping boundary.
 *
 * Compose screens and ViewModels depend only on this. The Mappls-specific
 * implementation, its endpoints and its credentials stay behind it, so the
 * provider can be swapped and tests can use a fake without any network.
 */
interface MappingRepository {

    /** True when credentials are present. UI uses this to explain itself. */
    val isConfigured: Boolean

    /** Address text to coordinates. */
    suspend fun geocode(address: String): MappingResult<ResolvedPlace>

    /** Coordinates to a human-readable address. */
    suspend fun reverseGeocode(point: GeoPoint): MappingResult<ResolvedPlace>

    /** Driving route between two points. */
    suspend fun route(from: GeoPoint, to: GeoPoint): MappingResult<RouteSummary>
}
