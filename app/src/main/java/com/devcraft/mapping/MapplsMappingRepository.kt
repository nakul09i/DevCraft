package com.devcraft.mapping

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Mappls (MapMyIndia) REST implementation of [MappingRepository].
 *
 * Credentials are injected, never hardcoded - see MAPPLS_API_KEY in
 * local.properties. With no key every call returns [MappingResult.NotConfigured]
 * and no request is made, so the app stays fully functional offline.
 *
 * STATUS: endpoints wired against Mappls' documented REST API shapes but NOT
 * verified against the live service, because no key was available. See
 * MAPPLS_SETUP.md.
 */
class MapplsMappingRepository(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) : MappingRepository {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun geocode(address: String): MappingResult<ResolvedPlace> {
        if (!isConfigured) return MappingResult.NotConfigured
        if (address.isBlank()) return MappingResult.NoResult

        val url = "$baseUrl/$apiKey/geo_code?addr=${address.urlEncoded()}"
        return request(url) { json ->
            val results = json.getAsJsonArray("results")
            if (results == null || results.size() == 0) return@request null
            val first = results[0].asJsonObject
            val lat = first.optDouble("lat") ?: return@request null
            val lng = first.optDouble("lng") ?: return@request null
            ResolvedPlace(
                point = GeoPoint(lat, lng),
                formattedAddress = first.optString("formatted_address"),
                placeId = first.optString("eLoc"),
            )
        }
    }

    override suspend fun reverseGeocode(point: GeoPoint): MappingResult<ResolvedPlace> {
        if (!isConfigured) return MappingResult.NotConfigured
        if (!point.isValid) return MappingResult.NoResult

        val url = "$baseUrl/$apiKey/rev_geocode?lat=${point.latitude}&lng=${point.longitude}"
        return request(url) { json ->
            val results = json.getAsJsonArray("results")
            if (results == null || results.size() == 0) return@request null
            val first = results[0].asJsonObject
            ResolvedPlace(
                point = point,
                formattedAddress = first.optString("formatted_address"),
                placeId = first.optString("eLoc"),
            )
        }
    }

    override suspend fun route(from: GeoPoint, to: GeoPoint): MappingResult<RouteSummary> {
        if (!isConfigured) return MappingResult.NotConfigured
        if (!from.isValid || !to.isValid) return MappingResult.NoResult

        val coords = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
        val url = "$baseUrl/$apiKey/route_adv/driving/$coords?geometries=polyline&overview=simplified"
        return request(url) { json ->
            val routes = json.getAsJsonArray("routes")
            if (routes == null || routes.size() == 0) return@request null
            val first = routes[0].asJsonObject
            val distance = first.optDouble("distance") ?: return@request null
            val duration = first.optDouble("duration") ?: return@request null
            RouteSummary(
                distanceMeters = distance.toInt(),
                durationSeconds = duration.toInt(),
                geometry = first.optString("geometry"),
            )
        }
    }

    /**
     * Single place where network failure is classified. An IOException is
     * treated as [MappingResult.Offline] so callers fall back to cached
     * coordinates instead of surfacing a crash.
     */
    private suspend fun <T> request(
        url: String,
        parse: (JsonObject) -> T?,
    ): MappingResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        401, 403 -> MappingResult.Failure("Mappls rejected the credentials (HTTP ${response.code})")
                        else -> MappingResult.Failure("Mappls request failed (HTTP ${response.code})")
                    }
                }
                if (body.isNullOrBlank()) return@withContext MappingResult.NoResult

                val json = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@withContext MappingResult.Failure("Unexpected Mappls response shape")

                parse(json)?.let { MappingResult.Success(it) } ?: MappingResult.NoResult
            }
        } catch (e: IOException) {
            MappingResult.Offline
        } catch (e: Exception) {
            MappingResult.Failure(e.message ?: "Mapping request failed", e)
        }
    }

    private companion object {
        const val BASE_URL = "https://apis.mappls.com/advancedmaps/v1"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

        fun JsonObject.optDouble(key: String): Double? =
            get(key)?.takeIf { !it.isJsonNull }?.asString?.toDoubleOrNull()

        fun JsonObject.optString(key: String): String? =
            get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    }
}
