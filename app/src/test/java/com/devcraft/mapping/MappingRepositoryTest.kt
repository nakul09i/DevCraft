package com.devcraft.mapping

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MappingRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mappls(key: String = "test-key") = MapplsMappingRepository(
        apiKey = key,
        baseUrl = server.url("/advancedmaps/v1").toString().trimEnd('/'),
    )

    // --- credentials boundary ---

    @Test
    fun blankKeyReportsNotConfiguredAndMakesNoRequest() = runBlocking {
        val repo = mappls(key = "")
        assertFalse(repo.isConfigured)
        assertEquals(MappingResult.NotConfigured, repo.geocode("Indore"))
        assertEquals(MappingResult.NotConfigured, repo.reverseGeocode(GeoPoint(22.7, 75.8)))
        assertEquals(
            MappingResult.NotConfigured,
            repo.route(GeoPoint(22.7, 75.8), GeoPoint(23.2, 77.4)),
        )
        assertEquals("no HTTP call should be attempted without a key", 0, server.requestCount)
    }

    @Test
    fun unconfiguredProviderIsHonestRatherThanFake() = runBlocking {
        assertFalse(UnconfiguredMappingRepository.isConfigured)
        assertEquals(MappingResult.NotConfigured, UnconfiguredMappingRepository.geocode("Indore"))
    }

    // --- geocoding ---

    @Test
    fun geocodeParsesMapplsResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[{"formatted_address":"Indore, Madhya Pradesh",
                "lat":"22.7196","lng":"75.8577","eLoc":"ABC123"}]}
                """.trimIndent()
            )
        )
        val result = mappls().geocode("Indore")
        assertTrue("expected success, got $result", result is MappingResult.Success)
        val place = (result as MappingResult.Success).value
        assertEquals(22.7196, place.point.latitude, 0.0001)
        assertEquals(75.8577, place.point.longitude, 0.0001)
        assertEquals("Indore, Madhya Pradesh", place.formattedAddress)
        assertEquals("ABC123", place.placeId)
    }

    @Test
    fun geocodeWithEmptyResultsIsNoResult() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"results":[]}"""))
        assertEquals(MappingResult.NoResult, mappls().geocode("nowhere at all"))
    }

    @Test
    fun blankAddressIsNoResultWithoutCallingOut() = runBlocking {
        assertEquals(MappingResult.NoResult, mappls().geocode("   "))
        assertEquals(0, server.requestCount)
    }

    // --- failure classification ---

    @Test
    fun rejectedCredentialsAreReportedDistinctly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val result = mappls().geocode("Indore")
        assertTrue(result is MappingResult.Failure)
        assertTrue(
            "message should name the credential problem",
            (result as MappingResult.Failure).message.contains("credentials"),
        )
    }

    @Test
    fun serverErrorIsFailureNotCrash() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertTrue(mappls().geocode("Indore") is MappingResult.Failure)
    }

    @Test
    fun unreachableHostIsOfflineSoCallersCanUseCache() = runBlocking {
        // Shutting the server down makes the connection fail with an IOException,
        // which must be classified as Offline, not a hard failure.
        server.shutdown()
        assertEquals(MappingResult.Offline, mappls().geocode("Indore"))
    }

    @Test
    fun malformedJsonIsFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))
        assertTrue(mappls().geocode("Indore") is MappingResult.Failure)
    }

    // --- routing ---

    @Test
    fun routeParsesDistanceAndDuration() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"routes":[{"distance":"18500","duration":"2400","geometry":"abc_def"}]}"""
            )
        )
        val result = mappls().route(GeoPoint(22.7196, 75.8577), GeoPoint(22.52, 75.92))
        assertTrue(result is MappingResult.Success)
        val route = (result as MappingResult.Success).value
        assertEquals(18500, route.distanceMeters)
        assertEquals(2400, route.durationSeconds)
        assertEquals("abc_def", route.geometry)
    }

    @Test
    fun routeRejectsInvalidCoordinates() = runBlocking {
        val result = mappls().route(GeoPoint(0.0, 0.0), GeoPoint(22.52, 75.92))
        assertEquals(MappingResult.NoResult, result)
        assertEquals(0, server.requestCount)
    }

    // --- GeoPoint validation ---

    @Test
    fun geoPointValidityRules() {
        assertTrue(GeoPoint(22.7196, 75.8577).isValid)
        assertFalse("null island is treated as absent", GeoPoint(0.0, 0.0).isValid)
        assertFalse(GeoPoint(91.0, 75.0).isValid)
        assertFalse(GeoPoint(22.0, 181.0).isValid)
    }

    // --- fake provider, used by the UI in demo mode and by other tests ---

    @Test
    fun fakeGeocodesKnownPlaces() = runBlocking {
        val result = FakeMappingRepository().geocode("deliver to Indore please")
        assertTrue(result is MappingResult.Success)
        assertEquals(22.7196, (result as MappingResult.Success).value.point.latitude, 0.001)
    }

    @Test
    fun fakeCanSimulateOffline() = runBlocking {
        val repo = FakeMappingRepository(forced = MappingResult.Offline)
        assertEquals(MappingResult.Offline, repo.geocode("Indore"))
        assertEquals(MappingResult.Offline, repo.route(GeoPoint(22.7, 75.8), GeoPoint(23.2, 77.4)))
    }

    @Test
    fun fakeRouteDistanceIsPlausible() = runBlocking {
        // Indore -> Bhopal is roughly 170 km straight line
        val result = FakeMappingRepository().route(GeoPoint(22.7196, 75.8577), GeoPoint(23.2599, 77.4126))
        assertTrue(result is MappingResult.Success)
        val km = (result as MappingResult.Success).value.distanceMeters / 1000
        assertTrue("expected ~170km, got ${km}km", km in 150..190)
    }
}
