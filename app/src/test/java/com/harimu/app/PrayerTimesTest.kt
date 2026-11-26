package com.harimu.app

import com.harimu.app.core.prayer.HttpPrayerTimesApi
import com.harimu.app.core.prayer.PrayerCity
import com.harimu.app.core.prayer.PrayerErrorKind
import com.harimu.app.core.prayer.PrayerSchedule
import com.harimu.app.core.prayer.PrayerTimesApi
import com.harimu.app.core.prayer.PrayerTimesException
import com.harimu.app.core.prayer.PrayerTimesJson
import com.harimu.app.core.prayer.PrayerTimesRepository
import com.harimu.app.core.prayer.pickCity
import com.harimu.app.core.prayer.userMessage
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class PrayerTimesTest {

    // Respons asli api.myquran.com (diambil 2026-11-25), disimpan sebagai fixture.
    private val citiesFixture = """
        {"status":true,"request":{"path":"/sholat/kota/cari/yogyakarta"},"keyword":"yogyakarta",
         "data":[{"id":"1505","lokasi":"KOTA YOGYAKARTA"}]}
    """.trimIndent()

    private val scheduleFixture = """
        {"status":true,"request":{"path":"/sholat/jadwal/1206/2026-11-25","year":"2026","month":"11","date":"25"},
         "data":{"id":1206,"lokasi":"KAB. CIANJUR","daerah":"JAWA BARAT","jadwal":{
           "tanggal":"Rabu, 25/11/2026","imsak":"03:53","subuh":"04:03","terbit":"05:21","dhuha":"05:50",
           "dzuhur":"11:42","ashar":"15:05","maghrib":"17:56","isya":"19:10","date":"2026-11-25"}}}
    """.trimIndent()

    @Test
    fun parsesCitySearchResponse() {
        val cities = PrayerTimesJson.parseCities(citiesFixture)
        assertEquals(1, cities.size)
        assertEquals(PrayerCity(id = "1505", name = "KOTA YOGYAKARTA"), cities.first())
    }

    @Test
    fun parsesScheduleResponseIntoEveryField() {
        val schedule = PrayerTimesJson.parseSchedule(scheduleFixture)
        assertEquals("1206", schedule.cityId)
        assertEquals("KAB. CIANJUR", schedule.city)
        assertEquals("JAWA BARAT", schedule.region)
        assertEquals("Rabu, 25/11/2026", schedule.dateLabel)
        assertEquals("2026-11-25", schedule.isoDate)
        assertEquals("03:53", schedule.imsak)
        assertEquals("04:03", schedule.subuh)
        assertEquals("17:56", schedule.maghrib)
        assertEquals("19:10", schedule.isya)
    }

    @Test
    fun rejectsUnsuccessfulOrUnreadablePayloads() {
        val statusFalse = """{"status":false,"data":null}"""
        val broken = "<html>not json</html>"
        assertEquals(
            PrayerErrorKind.INVALID_RESPONSE,
            runCatching { PrayerTimesJson.parseSchedule(statusFalse) }.exceptionOrNull()
                .let { (it as PrayerTimesException).kind }
        )
        assertEquals(
            PrayerErrorKind.INVALID_RESPONSE,
            runCatching { PrayerTimesJson.parseCities(broken) }.exceptionOrNull()
                .let { (it as PrayerTimesException).kind }
        )
    }

    @Test
    fun repositoryLoadsScheduleForMatchingCity() = runBlocking {
        val api = FakePrayerTimesApi(
            cities = listOf(PrayerCity("1505", "KOTA YOGYAKARTA")),
            schedule = fakeSchedule("1505", "KOTA YOGYAKARTA")
        )
        val result = PrayerTimesRepository(api).load("  yogyakarta ", LocalDate.of(2026, 11, 25))
        assertTrue(result.isSuccess)
        val schedule = result.getOrThrow()
        assertEquals("KOTA YOGYAKARTA", schedule.city)
        assertEquals(LocalDate.of(2026, 11, 25), api.requestedDate)
    }

    @Test
    fun repositoryRejectsBlankQueryBeforeCallingNetwork() = runBlocking {
        val api = FakePrayerTimesApi(cities = listOf(PrayerCity("1", "JAKARTA")))
        val result = PrayerTimesRepository(api).load("   ", LocalDate.of(2026, 11, 25))
        assertEquals(PrayerErrorKind.EMPTY_QUERY, (result.exceptionOrNull() as PrayerTimesException).kind)
        assertEquals(0, api.cityCalls)
    }

    @Test
    fun repositoryReportsUnknownCityAndNetworkFailure() = runBlocking {
        val empty = FakePrayerTimesApi(cities = emptyList())
        val notFound = PrayerTimesRepository(empty).load("nowhere", LocalDate.of(2026, 11, 25))
        assertEquals(PrayerErrorKind.CITY_NOT_FOUND, (notFound.exceptionOrNull() as PrayerTimesException).kind)

        val offline = FakePrayerTimesApi(cities = listOf(PrayerCity("1", "JAKARTA")), failure = IOException("offline"))
        val network = PrayerTimesRepository(offline).load("jakarta", LocalDate.of(2026, 11, 25))
        val error = network.exceptionOrNull() as PrayerTimesException
        assertEquals(PrayerErrorKind.NETWORK, error.kind)
        assertTrue(error.userMessage(isEnglish = false).contains("koneksi"))
        assertTrue(error.userMessage(isEnglish = true).contains("internet"))
    }

    @Test
    fun pickCityPrefersNameMatchThenFirstResult() {
        val cities = listOf(PrayerCity("1", "KAB. BANDUNG"), PrayerCity("2", "KOTA BANDUNG"))
        assertEquals("2", pickCity(cities, "kota bandung")?.id)
        assertEquals("1", pickCity(cities, "bandung")?.id)
        assertEquals(null, pickCity(emptyList(), "bandung"))
    }

    @Test
    fun core15IsRegisteredAsADedicatedNetworkTool() {
        val registered = ToolRegistry.findById("CORE-15")
        assertNotNull("CORE-15 must be registered", registered)
        val tool = requireNotNull(registered)
        assertFalse(tool.isStub)
        assertEquals(ToolGroup.CORE, tool.group)
        assertTrue("Prayer times needs internet", tool.requiresNetwork)
        assertEquals(DedicatedScreenRoute.PRAYER_TIMES, tool.dedicatedRoute)
        listOf(false, true).forEach { english ->
            assertTrue(tool.title(english).isNotBlank())
            assertTrue(tool.description(english).isNotBlank())
        }
    }

    @Test
    fun httpApiUsesTheDocumentedPublicBaseUrl() {
        assertEquals("https://api.myquran.com", HttpPrayerTimesApi.DEFAULT_BASE_URL)
    }

    private fun fakeSchedule(id: String, name: String) = PrayerSchedule(
        cityId = id,
        city = name,
        region = "DI YOGYAKARTA",
        dateLabel = "Rabu, 25/11/2026",
        isoDate = "2026-11-25",
        imsak = "04:00",
        subuh = "04:10",
        terbit = "05:25",
        dhuha = "05:55",
        dzuhur = "11:35",
        ashar = "15:00",
        maghrib = "17:50",
        isya = "19:05"
    )

    private class FakePrayerTimesApi(
        private val cities: List<PrayerCity>,
        private val schedule: PrayerSchedule? = null,
        private val failure: Throwable? = null
    ) : PrayerTimesApi {
        var cityCalls = 0
        var requestedDate: LocalDate? = null

        override suspend fun findCities(keyword: String): List<PrayerCity> {
            cityCalls++
            failure?.let { throw it }
            return cities
        }

        override suspend fun schedule(cityId: String, date: LocalDate): PrayerSchedule {
            requestedDate = date
            failure?.let { throw it }
            return schedule ?: throw IOException("no schedule")
        }
    }
}
