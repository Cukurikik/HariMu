package com.harimu.app

import com.harimu.app.core.area.AreaEntry
import com.harimu.app.core.area.AreaErrorKind
import com.harimu.app.core.area.AreaLookupApi
import com.harimu.app.core.area.AreaLookupException
import com.harimu.app.core.area.AreaLookupJson
import com.harimu.app.core.area.AreaLookupRepository
import com.harimu.app.core.area.HttpAreaLookupApi
import com.harimu.app.core.area.dedupeEntries
import com.harimu.app.core.area.userMessage
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

class AreaLookupTest {

    // Respons asli alamat.thecloudalert.com/api/cari/index/?keyword=Soreang (diambil 2026-11-26).
    private val searchFixture = """
        {"status":200,"message":"Berhasil","result":[
          {"negara":"Indonesia","provinsi":"Jawa Barat","kabkota":"Kabupaten Bandung",
           "kecamatan":"Soreang","desakel":"Bukit Harapan","kodepos":"40912"},
          {"negara":"Indonesia","provinsi":"Jawa Barat","kabkota":"Kabupaten Bandung",
           "kecamatan":"Soreang","desakel":"Soreang","kodepos":"40915"},
          {"negara":"Indonesia","provinsi":"Sulawesi Selatan","kabkota":"Kota Parepare",
           "kecamatan":"Soreang","desakel":"Sekarwangi","kodepos":"91132"}
        ]}
    """.trimIndent()

    private val emptyFixture = """{"status":200,"message":"Berhasil","result":[]}"""

    @Test
    fun parsesSearchResponseIntoAreaEntries() {
        val entries = AreaLookupJson.parseSearch(searchFixture)
        assertEquals(3, entries.size)
        assertEquals(
            AreaEntry(
                negara = "Indonesia",
                provinsi = "Jawa Barat",
                kabkota = "Kabupaten Bandung",
                kecamatan = "Soreang",
                desakel = "Bukit Harapan",
                kodepos = "40912"
            ),
            entries.first()
        )
        assertEquals("Kota Parepare", entries.last().kabkota)
        assertEquals("91132", entries.last().kodepos)
    }

    @Test
    fun emptyResultIsAValidAnswerNotAnError() = runBlocking {
        assertEquals(emptyList<AreaEntry>(), AreaLookupJson.parseSearch(emptyFixture))

        val api = FakeAreaLookupApi(entries = emptyList())
        val result = AreaLookupRepository(api).load("nowhere")
        assertTrue(result.isSuccess)
        assertEquals(emptyList<AreaEntry>(), result.getOrThrow())
        assertEquals(1, api.calls)
    }

    @Test
    fun rejectsUnreadablePayloads() {
        val serverError = """{"status":500,"message":"Gagal","result":[]}"""
        val broken = "<html>not json</html>"
        assertEquals(
            AreaErrorKind.INVALID_RESPONSE,
            runCatching { AreaLookupJson.parseSearch(serverError) }.exceptionOrNull()
                .let { (it as AreaLookupException).kind }
        )
        assertEquals(
            AreaErrorKind.INVALID_RESPONSE,
            runCatching { AreaLookupJson.parseSearch(broken) }.exceptionOrNull()
                .let { (it as AreaLookupException).kind }
        )
    }

    @Test
    fun repositoryRejectsBlankAndTooShortQueriesBeforeCallingNetwork() = runBlocking {
        val api = FakeAreaLookupApi(entries = listOf(sampleEntry()))
        val repository = AreaLookupRepository(api)

        val blank = repository.load("   ")
        assertEquals(AreaErrorKind.EMPTY_QUERY, (blank.exceptionOrNull() as AreaLookupException).kind)

        val tooShort = repository.load(" s ")
        assertEquals(AreaErrorKind.TOO_SHORT, (tooShort.exceptionOrNull() as AreaLookupException).kind)

        assertEquals(0, api.calls)
    }

    @Test
    fun repositoryMapsNetworkFailureAndKeepsHttpCode() = runBlocking {
        val offline = FakeAreaLookupApi(failure = IOException("offline"))
        val network = AreaLookupRepository(offline).load("soreang")
        val error = network.exceptionOrNull() as AreaLookupException
        assertEquals(AreaErrorKind.NETWORK, error.kind)
        assertTrue(error.userMessage(isEnglish = false).contains("koneksi"))
        assertTrue(error.userMessage(isEnglish = true).contains("internet"))

        val serverDown = FakeAreaLookupApi(failure = AreaLookupException(AreaErrorKind.HTTP, httpCode = 503))
        val http = AreaLookupRepository(serverDown).load("soreang")
        val httpError = http.exceptionOrNull() as AreaLookupException
        assertEquals(AreaErrorKind.HTTP, httpError.kind)
        assertEquals(503, httpError.httpCode)
    }

    @Test
    fun repositoryDeduplicatesIdenticalRowsAndCapsResultSize() = runBlocking {
        val repeated = listOf(sampleEntry(), sampleEntry(), sampleEntry())
        assertEquals(1, repeated.dedupeEntries().size)

        val many = (1..(AreaLookupRepository.MAX_RESULTS + 25)).map { index ->
            sampleEntry().copy(desakel = "Desa $index", kodepos = "40$index")
        }
        val result = AreaLookupRepository(FakeAreaLookupApi(entries = many)).load("desa")
        assertEquals(AreaLookupRepository.MAX_RESULTS, result.getOrThrow().size)
    }

    @Test
    fun core16IsRegisteredAsADedicatedNetworkTool() {
        val registered = ToolRegistry.findById("CORE-16")
        assertNotNull("CORE-16 must be registered", registered)
        val tool = requireNotNull(registered)
        assertFalse(tool.isStub)
        assertEquals(ToolGroup.CORE, tool.group)
        assertEquals("REST", tool.engineLabel)
        assertTrue("Area lookup needs internet", tool.requiresNetwork)
        assertEquals(DedicatedScreenRoute.AREA_LOOKUP, tool.dedicatedRoute)
        listOf(false, true).forEach { english ->
            assertTrue(tool.title(english).isNotBlank())
            assertTrue(tool.description(english).isNotBlank())
        }
    }

    @Test
    fun httpApiUsesDocumentedBaseUrlAndEncodesKeyword() {
        assertEquals("https://alamat.thecloudalert.com", HttpAreaLookupApi.DEFAULT_BASE_URL)

        val url = HttpAreaLookupApi.searchUrl(HttpAreaLookupApi.DEFAULT_BASE_URL, "kota bandung")
        assertEquals("https://alamat.thecloudalert.com/api/cari/index/", url.toString().substringBefore("?"))
        assertEquals("kota bandung", url.queryParameter("keyword"))
        assertTrue(url.encodedQuery?.contains("%20") == true)
    }

    private fun sampleEntry() = AreaEntry(
        negara = "Indonesia",
        provinsi = "Jawa Barat",
        kabkota = "Kabupaten Bandung",
        kecamatan = "Soreang",
        desakel = "Bukit Harapan",
        kodepos = "40912"
    )

    private class FakeAreaLookupApi(
        private val entries: List<AreaEntry> = emptyList(),
        private val failure: Throwable? = null
    ) : AreaLookupApi {
        var calls = 0

        override suspend fun search(keyword: String): List<AreaEntry> {
            calls++
            failure?.let { throw it }
            return entries
        }
    }
}
