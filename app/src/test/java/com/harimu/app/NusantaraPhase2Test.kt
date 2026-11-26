package com.harimu.app

import com.harimu.app.core.nusantara.FootballJson
import com.harimu.app.core.nusantara.FootballLeague
import com.harimu.app.core.nusantara.NusantaraErrorKind
import com.harimu.app.core.nusantara.NusantaraException
import com.harimu.app.core.nusantara.PesantrenJson
import com.harimu.app.core.nusantara.QuranJson
import com.harimu.app.core.nusantara.SchoolApi
import com.harimu.app.core.nusantara.SchoolJson
import com.harimu.app.core.nusantara.SchoolPage
import com.harimu.app.core.nusantara.SchoolRepository
import com.harimu.app.core.nusantara.SundaneseJson
import com.harimu.app.core.nusantara.TtsJson
import com.harimu.app.core.nusantara.filterLeagues
import com.harimu.app.core.nusantara.filterPesantren
import com.harimu.app.core.nusantara.filterSurahs
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NusantaraPhase2Test {

    // Respons asli api-sekolah-indonesia.vercel.app/sekolah?nama=sman%201&page=1 (diringkas).
    private val schoolFixture = """
        {"creator":"Alwan (wanrabbae)","status":"success",
         "dataSekolah":[
           {"npsn":"20104775","sekolah":"SD MELANIA III","bentuk":"SD","status":"S",
            "alamat_jalan":"Jl. Percetakan Negara No. 31","propinsi":"Prov. D.K.I. Jakarta",
            "kabupaten_kota":"Kota Jakarta Pusat","kecamatan":"Kec. Cempaka Putih",
            "lintang":"-6.1824000","bujur":"106.8667000"},
           {"npsn":"20104799","sekolah":"SD PASKALIS I","bentuk":"SD","status":"S",
            "alamat_jalan":"Jl. Bala Dewa F.16","propinsi":"Prov. D.K.I. Jakarta",
            "kabupaten_kota":"Kota Jakarta Pusat","kecamatan":"Kec. Johar Baru",
            "lintang":"-6.1772000","bujur":"106.8483000"}],
         "total_data":215373,"page":1,"per_page":5}
    """.trimIndent()

    // Respons asli hibersunda.vercel.app/undakusukbasa/lebet.
    private val sundaneseFixture = """
        [{"sorangan":"lebet","batur":"lebet","loma":"abus, asup","bindo":"masuk","english":"enter"},
         {"sorangan":"ngalebetan","batur":"ngalebetan","loma":"eusi, ngeusian","bindo":"mengisi",
          "english":"fill"},
         {"sorangan":"","batur":"","loma":"","bindo":"","english":""}]
    """.trimIndent()

    // Respons asli kunci-tts-api.vercel.app/api/answers?question=tidak (bintang sebagai teks).
    private val ttsFixture = """
        {"title":"Kunci jawaban TTS Tidak","total":2,
         "answers":[{"stars":"5","word":"TAK","clue":"Tidak"},
                    {"stars":4,"word":"ENGGAK","clue":"Tidak"}]}
    """.trimIndent()

    // Respons asli quran-api-id.vercel.app/surah (diringkas).
    private val surahListFixture = """
        {"code":200,"status":"OK.","data":[
          {"number":18,"numberOfVerses":110,
           "name":{"transliteration":"Al-Kahf","translation":{"en":"The Cave","id":"Goa"}},
           "revelation":{"id":"Makkiyyah"}}]}
    """.trimIndent()

    // Respons asli quran-api-id.vercel.app/surah/18 (ayat pertama, diringkas).
    private val surahDetailFixture = """
        {"code":200,"status":"OK.","data":{"number":18,"numberOfVerses":110,
         "name":{"transliteration":"Al-Kahf","translation":{"en":"The Cave","id":"Goa"}},
         "revelation":{"id":"Makkiyyah"},
         "verses":[{"number":{"inQuran":2141,"inSurah":1},
           "text":{"arab":"الْحَمْدُ لِلَّهِ","transliteration":{"en":"Alhamdu lillaahil lazeee"}},
           "translation":{"en":"[All] praise is [due] to Allah","id":"Segala puji bagi Allah"},
           "audio":{"primary":"https://cdn.alquran.cloud/media/audio/ayah/ar.alafasy/2141"}}]}}
    """.trimIndent()

    private val leaguesFixture = """
        {"status":true,"data":[
          {"id":"eng.1","name":"English Premier League","abbr":"Premier League"},
          {"id":"idn.1","name":"Indonesian Liga 1","abbr":"Liga 1"}]}
    """.trimIndent()

    private val standingsFixture = """
        {"status":true,"data":{"name":"English Premier League","seasonDisplay":"2024-2025",
         "standings":[{"team":{"name":"Liverpool","displayName":"Liverpool"},
           "stats":[{"name":"rank","displayValue":"1"},{"name":"gamesPlayed","displayValue":"38"},
                    {"name":"wins","displayValue":"25"},{"name":"ties","displayValue":"9"},
                    {"name":"losses","displayValue":"4"},{"name":"pointDifferential","displayValue":"+45"},
                    {"name":"points","displayValue":"84"}]}]}}
    """.trimIndent()

    private val provincesFixture = """[{"id":"32","nama":"Jawa Barat"},{"id":"11","nama":"Aceh"}]"""

    private val pesantrenFixture = """
        [{"id":"157","nama":"Al-Amanah","nspp":"510032061290","alamat":"Pajaten Ds. Pasirhuni.",
          "kyai":"Aj. Sahid Idrus"},
         {"id":"158","nama":"Nurul Huda","nspp":"510032060957","alamat":"Ciaren Ds. Karangjaya.",
          "kyai":"Aj Komarudin"}]
    """.trimIndent()

    @Test
    fun parsesSchoolPageWithPagingMetadata() {
        val page = SchoolJson.parse(schoolFixture)
        assertEquals(2, page.schools.size)
        assertEquals(215373, page.total)
        assertEquals(1, page.page)
        assertEquals(5, page.perPage)
        assertTrue(page.hasMore)

        val first = page.schools.first()
        assertEquals("20104775", first.npsn)
        assertEquals("SD MELANIA III", first.name)
        assertEquals("SD", first.form)
        assertEquals("Kec. Cempaka Putih", first.district)
        assertEquals("-6.1824000", first.latitude)
    }

    @Test
    fun schoolPageStopsAskingForMoreOnTheLastPage() {
        val last = SchoolJson.parse(schoolFixture.replace("\"page\":1", "\"page\":43075"))
        assertFalse(last.hasMore)
    }

    @Test
    fun parsesSundaneseEntriesAndDropsBlankOnes() {
        val entries = SundaneseJson.parse(sundaneseFixture)
        assertEquals(2, entries.size)
        assertEquals("lebet", entries.first().sorangan)
        assertEquals("abus, asup", entries.first().loma)
        assertEquals("masuk", entries.first().indonesian)
        assertEquals("fill", entries.last().english)
    }

    @Test
    fun parsesCrosswordAnswersWithTolerantStars() {
        val result = TtsJson.parse(ttsFixture)
        assertEquals("Kunci jawaban TTS Tidak", result.title)
        assertEquals(2, result.total)
        assertEquals("TAK", result.answers.first().word)
        assertEquals(5, result.answers.first().stars)
        assertEquals(4, result.answers.last().stars)
    }

    @Test
    fun parsesQuranSurahListAndVerses() {
        val surahs = QuranJson.parseSurahs(surahListFixture)
        assertEquals(1, surahs.size)
        val surah = surahs.first()
        assertEquals(18, surah.number)
        assertEquals("Al-Kahf", surah.name)
        assertEquals("Goa", surah.translationId)
        assertEquals("The Cave", surah.translationEn)
        assertEquals(110, surah.verseCount)
        assertEquals("Makkiyyah", surah.revelationId)

        val detail = QuranJson.parseSurah(surahDetailFixture)
        assertEquals(18, detail.surah.number)
        assertEquals(1, detail.verses.size)
        val verse = detail.verses.first()
        assertEquals(1, verse.number)
        assertTrue(verse.arabic.contains("الْحَمْدُ"))
        assertEquals("Alhamdu lillaahil lazeee", verse.latin)
        assertEquals("Segala puji bagi Allah", verse.translationId)
        assertTrue(verse.audioUrl.endsWith("/2141"))
    }

    @Test
    fun rejectsQuranPayloadWithoutSurahData() {
        val error = runCatching { QuranJson.parseSurah("""{"code":200,"data":null}""") }
            .exceptionOrNull()
        assertEquals(NusantaraErrorKind.INVALID_RESPONSE, (error as NusantaraException).kind)
        assertEquals("Quran API ID", error.service)
    }

    @Test
    fun parsesLeaguesAndStandingsStats() {
        val leagues = FootballJson.parseLeagues(leaguesFixture)
        assertEquals(2, leagues.size)
        assertEquals("English Premier League", leagues.first().name)
        assertEquals("Premier League", leagues.first().abbreviation)

        val standings = FootballJson.parseStandings(standingsFixture)
        assertEquals("English Premier League", standings.league)
        assertEquals("2024-2025", standings.season)
        val row = standings.rows.single()
        assertEquals(1, row.rank)
        assertEquals("Liverpool", row.team)
        assertEquals(38, row.played)
        assertEquals(25, row.wins)
        assertEquals(9, row.draws)
        assertEquals(4, row.losses)
        assertEquals("+45", row.goalDifference)
        assertEquals(84, row.points)
    }

    @Test
    fun parsesPesantrenRegionsAndDirectory() {
        val provinces = PesantrenJson.parseRegions(provincesFixture)
        assertEquals(listOf("Aceh", "Jawa Barat"), provinces.map { it.name })
        assertEquals("11", provinces.first().id)

        val pesantren = PesantrenJson.parsePesantren(pesantrenFixture)
        assertEquals(2, pesantren.size)
        assertEquals("Al-Amanah", pesantren.first().name)
        assertEquals("510032061290", pesantren.first().registrationNumber)
        assertEquals("Aj. Sahid Idrus", pesantren.first().cleric)
    }

    @Test
    fun repositoryMapsNetworkFailureToServiceMessage() = runBlocking {
        val api = object : SchoolApi {
            override suspend fun search(query: String, page: Int): SchoolPage = throw IOException("offline")
        }
        val error = SchoolRepository(api).search("sman").exceptionOrNull() as NusantaraException
        assertEquals(NusantaraErrorKind.NETWORK, error.kind)
        assertTrue(error.userMessage(isEnglish = false).contains("koneksi"))
        assertTrue(error.userMessage(isEnglish = true).contains("api-sekolah-indonesia"))
    }

    @Test
    fun filtersNarrowListsInBothLanguages() {
        val surahs = QuranJson.parseSurahs(surahListFixture)
        assertEquals(1, surahs.filterSurahs("kahf").size)
        assertEquals(1, surahs.filterSurahs("18").size)
        assertEquals(0, surahs.filterSurahs("yasin").size)

        val leagues = FootballJson.parseLeagues(leaguesFixture)
        assertEquals(1, leagues.filterLeagues("premier").size)
        assertEquals(2, leagues.filterLeagues("").size)

        val pesantren = PesantrenJson.parsePesantren(pesantrenFixture)
        assertEquals(1, pesantren.filterPesantren("nurul").size)
        assertEquals(1, pesantren.filterPesantren("Karangjaya").size)
        assertEquals(0, pesantren.filterPesantren("tidak ada").size)
    }

    @Test
    fun leagueFilterKeepsAbbreviationMatches() {
        val leagues = listOf(FootballLeague("idn.1", "Indonesian Liga 1", "Liga 1"))
        assertEquals(1, leagues.filterLeagues("Liga").size)
    }

    @Test
    fun everyPhaseTwoNusantaraToolIsRegisteredAsADedicatedNetworkTool() {
        val expected = mapOf(
            "NUS-07" to DedicatedScreenRoute.SCHOOLS,
            "NUS-08" to DedicatedScreenRoute.SUNDANESE,
            "NUS-09" to DedicatedScreenRoute.CROSSWORD,
            "NUS-10" to DedicatedScreenRoute.QURAN,
            "NUS-11" to DedicatedScreenRoute.FOOTBALL,
            "NUS-12" to DedicatedScreenRoute.PESANTREN
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
        assertEquals(12, ToolRegistry.byGroup(ToolGroup.NUSANTARA).size)
        assertEquals(182, ToolRegistry.allTools.size)
    }
}
