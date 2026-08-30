package com.devcraft.mapping

/**
 * Deterministic in-memory mapping provider. Used by unit tests and available as
 * a demo provider so the map UI can be exercised with no key and no network.
 */
class FakeMappingRepository(
    override val isConfigured: Boolean = true,
    private val knownPlaces: Map<String, ResolvedPlace> = DEFAULT_PLACES,
    private val forced: MappingResult<Nothing>? = null,
) : MappingRepository {

    override suspend fun geocode(address: String): MappingResult<ResolvedPlace> {
        forced?.let { return it }
        if (!isConfigured) return MappingResult.NotConfigured
        val key = address.trim().lowercase()
        val hit = knownPlaces.entries.firstOrNull { key.contains(it.key) }?.value
        return hit?.let { MappingResult.Success(it) } ?: MappingResult.NoResult
    }

    override suspend fun reverseGeocode(point: GeoPoint): MappingResult<ResolvedPlace> {
        forced?.let { return it }
        if (!isConfigured) return MappingResult.NotConfigured
        if (!point.isValid) return MappingResult.NoResult
        return MappingResult.Success(
            ResolvedPlace(
                point = point,
                formattedAddress = "%.4f, %.4f".format(point.latitude, point.longitude),
                placeId = null,
            )
        )
    }

    override suspend fun route(from: GeoPoint, to: GeoPoint): MappingResult<RouteSummary> {
        forced?.let { return it }
        if (!isConfigured) return MappingResult.NotConfigured
        if (!from.isValid || !to.isValid) return MappingResult.NoResult
        val meters = haversineMeters(from, to).toInt()
        return MappingResult.Success(
            RouteSummary(
                distanceMeters = meters,
                // ~30 km/h average for Indian city delivery
                durationSeconds = (meters / 8.33).toInt(),
                geometry = null,
            )
        )
    }

    companion object {
        val DEFAULT_PLACES: Map<String, ResolvedPlace> = mapOf(
            "indore" to ResolvedPlace(GeoPoint(22.7196, 75.8577), "Indore, Madhya Pradesh", "IND01"),
            "iit indore" to ResolvedPlace(GeoPoint(22.5200, 75.9200), "IIT Indore, Simrol", "IITI1"),
            "bhopal" to ResolvedPlace(GeoPoint(23.2599, 77.4126), "Bhopal, Madhya Pradesh", "BHO01"),
            "delhi" to ResolvedPlace(GeoPoint(28.6139, 77.2090), "New Delhi", "DEL01"),
        )

        fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
            return 2 * r * Math.asin(Math.sqrt(h))
        }
    }
}
