package com.harimu.app

import com.harimu.app.core.nusantara.BmkgCuacaJson
import com.harimu.app.core.nusantara.BmkgGempaJson
import com.harimu.app.core.nusantara.DoaApi
import com.harimu.app.core.nusantara.DoaJson
import com.harimu.app.core.nusantara.DoaRepository
import com.harimu.app.core.nusantara.EarthquakeApi
import com.harimu.app.core.nusantara.EarthquakeRepository
import com.harimu.app.core.nusantara.NusantaraErrorKind
import com.harimu.app.core.nusantara.NusantaraException
import com.harimu.app.core.nusantara.ReferenceJson
import com.harimu.app.core.nusantara.Region
import com.harimu.app.core.nusantara.RegionApi
import com.harimu.app.core.nusantara.RegionRepository
import com.harimu.app.core.nusantara.WilayahJson
import com.harimu.app.core.nusantara.userMessage
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

class NusantaraTest {

    // Respons asli data.bmkg.go.id/DataMKG/TEWS/autogempa.json (diambil 2026-11-26).
    private val latestQuakeFixture = """
        {"Infogempa":{"gempa":{"Tanggal":"08 Nov 2026","Jam":"01:54:12 WIB",
        "DateTime":"2026-11-07T18:54:12+00:00","Coordinates":"-4.01,122.69",
        "Magnitude":"3.3","Kedalaman":"5 km",
        "Wilayah":"Pusat gempa berada di darat 20 km timur Kendari",
        "Potensi":"Gempa ini dirasakan untuk diteruskan pada masyarakat","Dirasakan":"",
        "Shakemap":"20261108015412.mmi.jpg"}}}
    """.trimIndent()

    private val recentQuakesFixture = """
        {"Infogempa":{"gempa":[
          {"Tanggal":"08 Nov 2026","Jam":"01:54:12 WIB","Magnitude":"3.3","Kedalaman":"5 km",
           "Wilayah":"Pusat gempa berada di darat 20 km timur Kendari","Potensi":"-","Dirasakan":"II Kendari"},
          {"Tanggal":"07 Nov 2026","Jam":"22:10:30 WIB","Magnitude":"4.1","Kedalaman":"10 km",
           "Wilayah":"Pusat gempa berada di laut 45 km barat daya Bima","Potensi":"-","Dirasakan":""}
        ]}}
    """.trimIndent()

    // Respons asli api.bmkg.go.id/publik/prakiraan-cuaca?adm4=31.71.01.1001 (diringkas).
    private val forecastFixture = """
        {"lokasi":{"adm4":"31.71.01.1001","provinsi":"DKI Jakarta","kotkab":"Kota Adm. Jakarta Pusat",
         "kecamatan":"Gambir","desa":"Gambir"},
         "data":[{"cuaca":[[{"datetime":"2026-10-29T12:00:00Z","t":26,"hu":92,"weather":61,
           "weather_desc":"Hujan Ringan","weather_desc_en":"Light Rain","wd":"E","ws":1.7,
           "local_datetime":"2026-10-29 19:00:00",
           "image":"https://api-apps.bmkg.go.id/storage/icon/cuaca/hujan-ringan-pm.svg"}]]}]}
    """.trimIndent()

    private val provincesFixture = """[{"id":"11","name":"ACEH"},{"id":"31","name":"DKI JAKARTA"}]"""

    private val heroesFixture = """
        [{"name":"Abdul Malik Karim Amrullah","birth_year":1908,"death_year":1981,
          "description":"Sarjana Islam dan penulis","ascension_year":2011},
         {"name":"Fatmawati","birth_year":1923,"death_year":1980,
          "description":"Pembuat bendera nasional pertama","ascension_year":2000}]
    """.trimIndent()

    private val volcanoesFixture = """
        [{"nama":"Weh","bentuk":"stratovulkan","tinggi_meter":"617 meter",
          "estimasi_letusan_terakhir":"Pleistosen","geolokasi":"5.82°N 95.28°E"}]
    """.trimIndent()

    private val doasFixture = """
        [{"id":"1","doa":"Doa sebelum tidur","ayat":"بِسْمِكَ","latin":"Bismikallahumma",
          "artinya":"Dengan menyebut nama Allah"}]
    """.trimIndent()

    @Test
    fun parsesLatestAndRecentEarthquakes() {
        val latest = BmkgGempaJson.parseLatest(latestQuakeFixture)
        assertEquals("3.3", latest.magnitude)
        assertEquals("5 km", latest.depth)
        assertTrue(latest.region.contains("Kendari"))
        assertEquals("08 Nov 2026", latest.date)

        val recent = BmkgGempaJson.parseRecent(recentQuakesFixture)
        assertEquals(2, recent.size)
        assertTrue(recent.first().region.contains("Kendari"))
        assertEquals("4.1", recent.last().magnitude)
    }

    @Test
    fun parsesWeatherForecastAndFlattensSlots() {
        val forecast = BmkgCuacaJson.parse(forecastFixture)
        assertEquals("31.71.01.1001", forecast.location.adm4)
        assertEquals("Gambir", forecast.location.village)
        assertEquals(1, forecast.slots.size)
        val slot = forecast.slots.first()
        assertEquals(26, slot.temperatureC)
        assertEquals(92, slot.humidityPercent)
        assertEquals("Light Rain", slot.conditionEn)
        assertTrue(slot.iconUrl.endsWith("hujan-ringan-pm.svg"))
    }

    @Test
    fun parsesRegionHierarchyAndReferenceData() {
        val provinces = WilayahJson.parse(provincesFixture)
        assertEquals(listOf(Region("11", "ACEH"), Region("31", "DKI JAKARTA")), provinces)

        val heroes = ReferenceJson.parseHeroes(heroesFixture)
        assertEquals(2, heroes.size)
        assertEquals("1908", heroes.first().birthYear)
        assertEquals("2011", heroes.first().ascensionYear)

        val volcanoes = ReferenceJson.parseVolcanoes(volcanoesFixture)
        assertEquals("Weh", volcanoes.first().name)
        assertEquals("Pleistosen", volcanoes.first().lastEruption)

        val doas = DoaJson.parse(doasFixture)
        assertEquals("Doa sebelum tidur", doas.first().title)
        assertEquals("Bismikallahumma", doas.first().latin)
    }

    @Test
    fun rejectsUnreadablePayloads() {
        listOf(
            { BmkgGempaJson.parseLatest("<html>not json</html>") },
            { BmkgCuacaJson.parse("""{"lokasi":{},"data":[]}""") },
            { DoaJson.parse("[] bukan json") }
        ).forEach { block ->
            val error = runCatching(block).exceptionOrNull()
            assertEquals(NusantaraErrorKind.INVALID_RESPONSE, (error as NusantaraException).kind)
        }
    }

    @Test
    fun repositoryFallsBackWhenRecentFeedFails() = runBlocking {
        val api = FakeEarthquakeApi(recentFails = true)
        val snapshot = EarthquakeRepository(api).load().getOrThrow()
        assertEquals("3.3", snapshot.latest.magnitude)
        assertEquals(1, snapshot.recent.size)
    }

    @Test
    fun repositoryMapsNetworkFailureToServiceMessage() = runBlocking {
        val api = FakeEarthquakeApi(failure = IOException("offline"))
        val error = EarthquakeRepository(api).load().exceptionOrNull() as NusantaraException
        assertEquals(NusantaraErrorKind.NETWORK, error.kind)
        assertTrue(error.userMessage(isEnglish = false).contains("koneksi"))
        assertTrue(error.userMessage(isEnglish = true).contains("internet"))
        assertTrue(error.userMessage(isEnglish = true).contains("BMKG"))
    }

    @Test
    fun regionRepositorySortsLevelsAndDoaRepositoryLoadsList() = runBlocking {
        val regions = RegionRepository(FakeRegionApi()).provinces().getOrThrow()
        assertEquals(listOf("ACEH", "DKI JAKARTA"), regions.map { it.name })

        val doas = DoaRepository(FakeDoaApi()).all().getOrThrow()
        assertEquals(1, doas.size)
    }

    @Test
    fun everyNusantaraToolIsRegisteredAsADedicatedNetworkTool() {
        val expected = mapOf(
            "NUS-01" to DedicatedScreenRoute.EARTHQUAKE,
            "NUS-02" to DedicatedScreenRoute.BMKG_WEATHER,
            "NUS-03" to DedicatedScreenRoute.REGIONS,
            "NUS-04" to DedicatedScreenRoute.HEROES,
            "NUS-05" to DedicatedScreenRoute.VOLCANOES,
            "NUS-06" to DedicatedScreenRoute.DUAS
        )
        expected.forEach { (id, route) ->
            val registered = ToolRegistry.findById(id)
            assertNotNull("$id must be registered", registered)
            val tool = requireNotNull(registered)
            assertFalse(tool.isStub)
            assertEquals(ToolGroup.NUSANTARA, tool.group)
            assertTrue("$id needs internet", tool.requiresNetwork)
            assertEquals(route, tool.dedicatedRoute)
            assertTrue(tool.title(isEnglish = false).isNotBlank())
            assertTrue(tool.title(isEnglish = true).isNotBlank())
            assertTrue(tool.description(isEnglish = false).isNotBlank())
            assertTrue(tool.description(isEnglish = true).isNotBlank())
        }
        assertEquals(6, ToolRegistry.byGroup(ToolGroup.NUSANTARA).size)
    }

    private class FakeEarthquakeApi(
        private val failure: Throwable? = null,
        private val recentFails: Boolean = false
    ) : EarthquakeApi {
        override suspend fun latest(): com.harimu.app.core.nusantara.Earthquake {
            failure?.let { throw it }
            return BmkgGempaJson.parseLatest(latestQuakeFixtureForTest())
        }

        override suspend fun recent(): List<com.harimu.app.core.nusantara.Earthquake> {
            failure?.let { throw it }
            if (recentFails) throw IOException("feed down")
            return BmkgGempaJson.parseRecent(recentFixtureForTest())
        }

        private fun latestQuakeFixtureForTest() = """
            {"Infogempa":{"gempa":{"Tanggal":"08 Nov 2026","Jam":"01:54:12 WIB","Magnitude":"3.3",
            "Kedalaman":"5 km","Wilayah":"Pusat gempa di darat 20 km timur Kendari"}}}
        """.trimIndent()

        private fun recentFixtureForTest() = """
            {"Infogempa":{"gempa":[{"Tanggal":"08 Nov 2026","Jam":"01:54:12 WIB","Magnitude":"3.3",
            "Kedalaman":"5 km","Wilayah":"Pusat gempa di darat 20 km timur Kendari"}]}}
        """.trimIndent()
    }

    private class FakeRegionApi : RegionApi {
        override suspend fun provinces() = listOf(Region("31", "DKI JAKARTA"), Region("11", "ACEH"))
        override suspend fun regencies(provinceId: String) = emptyList<Region>()
        override suspend fun districts(regencyId: String) = emptyList<Region>()
        override suspend fun villages(districtId: String) = emptyList<Region>()
    }

    private class FakeDoaApi : DoaApi {
        override suspend fun all() = DoaJson.parse(
            """[{"id":"1","doa":"Doa sebelum tidur","ayat":"بِسْمِكَ","latin":"Bismikallahumma",
               "artinya":"Dengan menyebut nama Allah"}]"""
        )
    }
}
