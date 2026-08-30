package com.devcraft.mapping

import com.devcraft.BuildConfig

/**
 * Chooses the mapping implementation once, at the edge of the app.
 *
 * With a key present the real Mappls client is used. Without one we return a
 * repository that reports NotConfigured rather than a fake pretending to work -
 * a demo must never look live when it isn't.
 */
object MappingProvider {

    val apiKeyPresent: Boolean get() = BuildConfig.MAPPLS_API_KEY.isNotBlank()

    fun create(): MappingRepository =
        if (apiKeyPresent) {
            MapplsMappingRepository(apiKey = BuildConfig.MAPPLS_API_KEY)
        } else {
            UnconfiguredMappingRepository
        }
}

/** Honest no-op used when no credentials were supplied at build time. */
object UnconfiguredMappingRepository : MappingRepository {
    override val isConfigured: Boolean = false
    override suspend fun geocode(address: String) = MappingResult.NotConfigured
    override suspend fun reverseGeocode(point: GeoPoint) = MappingResult.NotConfigured
    override suspend fun route(from: GeoPoint, to: GeoPoint) = MappingResult.NotConfigured
}
