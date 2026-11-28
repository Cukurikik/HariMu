package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.harimu.app.core.area.AreaEntry
import com.harimu.app.core.area.AreaLookupApi
import com.harimu.app.core.area.AreaLookupRepository
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.nusantara.Doa
import com.harimu.app.core.nusantara.DoaApi
import com.harimu.app.core.nusantara.DoaRepository
import com.harimu.app.core.nusantara.Earthquake
import com.harimu.app.core.nusantara.EarthquakeApi
import com.harimu.app.core.nusantara.EarthquakeRepository
import com.harimu.app.core.nusantara.FootballApi
import com.harimu.app.core.nusantara.FootballLeague
import com.harimu.app.core.nusantara.FootballRepository
import com.harimu.app.core.nusantara.FootballStandings
import com.harimu.app.core.nusantara.Pesantren
import com.harimu.app.core.nusantara.PesantrenApi
import com.harimu.app.core.nusantara.PesantrenRegion
import com.harimu.app.core.nusantara.PesantrenRepository
import com.harimu.app.core.nusantara.QuranApi
import com.harimu.app.core.nusantara.QuranRepository
import com.harimu.app.core.nusantara.QuranSurah
import com.harimu.app.core.nusantara.QuranSurahDetail
import com.harimu.app.core.nusantara.QuranVerse
import com.harimu.app.core.nusantara.Region
import com.harimu.app.core.nusantara.RegionApi
import com.harimu.app.core.nusantara.RegionRepository
import com.harimu.app.core.nusantara.School
import com.harimu.app.core.nusantara.SchoolApi
import com.harimu.app.core.nusantara.SchoolPage
import com.harimu.app.core.nusantara.SchoolRepository
import com.harimu.app.core.nusantara.StandingRow
import com.harimu.app.core.nusantara.SundaneseApi
import com.harimu.app.core.nusantara.SundaneseEntry
import com.harimu.app.core.nusantara.SundaneseRepository
import com.harimu.app.core.nusantara.TtsAnswer
import com.harimu.app.core.nusantara.TtsApi
import com.harimu.app.core.nusantara.TtsRepository
import com.harimu.app.core.nusantara.TtsResult
import com.harimu.app.core.nusantara.WeatherApi
import com.harimu.app.core.nusantara.WeatherForecast
import com.harimu.app.core.nusantara.WeatherLocation
import com.harimu.app.core.nusantara.WeatherRepository
import com.harimu.app.core.nusantara.WeatherSlot
import com.harimu.app.core.prayer.PrayerCity
import com.harimu.app.core.prayer.PrayerSchedule
import com.harimu.app.core.prayer.PrayerTimesApi
import com.harimu.app.core.prayer.PrayerTimesRepository
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.feature.area.AreaLookupScreen
import com.harimu.app.feature.area.AreaLookupViewModel
import com.harimu.app.feature.nusantara.DoasScreen
import com.harimu.app.feature.nusantara.DoasViewModel
import com.harimu.app.feature.nusantara.EarthquakeScreen
import com.harimu.app.feature.nusantara.EarthquakeViewModel
import com.harimu.app.feature.nusantara.FootballScreen
import com.harimu.app.feature.nusantara.FootballViewModel
import com.harimu.app.feature.nusantara.NusantaraHubScreen
import com.harimu.app.feature.nusantara.NusantaraHubViewModel
import com.harimu.app.feature.nusantara.PesantrenScreen
import com.harimu.app.feature.nusantara.PesantrenViewModel
import com.harimu.app.feature.nusantara.QuranScreen
import com.harimu.app.feature.nusantara.QuranViewModel
import com.harimu.app.feature.nusantara.RegionsScreen
import com.harimu.app.feature.nusantara.RegionsViewModel
import com.harimu.app.feature.nusantara.SchoolScreen
import com.harimu.app.feature.nusantara.SchoolViewModel
import com.harimu.app.feature.nusantara.SundaneseScreen
import com.harimu.app.feature.nusantara.SundaneseViewModel
import com.harimu.app.feature.nusantara.TtsScreen
import com.harimu.app.feature.nusantara.TtsViewModel
import com.harimu.app.feature.nusantara.WeatherScreen
import com.harimu.app.feature.nusantara.WeatherViewModel
import com.harimu.app.feature.prayer.PrayerTimesScreen
import com.harimu.app.feature.prayer.PrayerTimesViewModel
import com.harimu.app.feature.prayer.findNextPrayerIndex
import com.harimu.app.feature.studios.BackupScreen
import com.harimu.app.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.IOException
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class NusantaraAndUtilitiesUiTest {

    @Test
    fun findNextPrayerIndexResolvesUpcomingOrWrapsToImsak() {
        val schedule = samplePrayerSchedule()
        assertEquals(0, findNextPrayerIndex(schedule, LocalTime.of(3, 30)))
        assertEquals(4, findNextPrayerIndex(schedule, LocalTime.of(10, 0))) // Dzuhur 11:42
        assertEquals(6, findNextPrayerIndex(schedule, LocalTime.of(17, 56))) // Exact Maghrib
        assertEquals(0, findNextPrayerIndex(schedule, LocalTime.of(21, 0))) // Wraps to Imsak
        val unparseable = schedule.copy(
            imsak = "",
            subuh = "",
            terbit = "",
            dhuha = "",
            dzuhur = "",
            ashar = "",
            maghrib = "",
            isya = ""
        )
        assertNull(findNextPrayerIndex(unparseable, LocalTime.of(12, 0)))
    }

    @Test
    fun nusantaraDestinationProvidesOwnTopBarWithoutEnteringBottomNav() {
        assertTrue(TopLevelDestination.NUSANTARA.providesOwnTopBar)
        assertFalse(TopLevelDestination.NUSANTARA.showInNavigationBar)
        assertEquals(listOf(TopLevelDestination.CATALOG), TopLevelDestination.navigationItems.filter { it.providesOwnTopBar })
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun nusantaraHubFiltersSearchesShowsQuickAccessAndResetsEmptyState() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val storeOwner = FakeVmOwner()
            seedViewModel(storeOwner, NusantaraHubViewModel(EarthquakeRepository(FakeQuakeApi())))

            var openedTool: Tool? = null
            setTestContent(activity, isEnglish = true, vmOwner = storeOwner) {
                NusantaraHubScreen(
                    container = container,
                    isEnglish = true,
                    onOpenTool = { openedTool = it },
                    onBack = {}
                )
            }

            val initial = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "nusantara_quake_card") != null
            }
            val tags = initial.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            val texts = initial.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
            assertNotNull("Missing nusantara_back_button; tags=$tags; texts=${texts.take(15)}", findByTag(initial, "nusantara_back_button"))
            assertNotNull("Missing nusantara_quick_CORE-15; tags=$tags", findByTag(initial, "nusantara_quick_CORE-15"))
            assertNotNull("Missing nusantara_quick_CORE-16; tags=$tags", findByTag(initial, "nusantara_quick_CORE-16"))
            assertTrue(hasTextContaining(initial, "Kendari"))

            val density = activity.resources.displayMetrics.density
            val backBounds = findByTag(initial, "nusantara_back_button")!!.boundsInRoot
            assertTrue(backBounds.width / density >= 47.5f)
            assertTrue(backBounds.height / density >= 47.5f)

            clickNode(findByTag(initial, "nusantara_quick_CORE-15")!!)
            flushMainUi(activity)
            assertEquals("CORE-15", openedTool?.id)

            setText(findByTag(initial, "nusantara_hub_search")!!, "zzznomatch")
            val emptyNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "nusantara_hub_reset") != null
            }
            assertNotNull(findByTag(emptyNodes, "nusantara_hub_reset"))

            clickNode(findByTag(emptyNodes, "nusantara_hub_reset")!!)
            val restored = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "nusantara_quake_card") != null
            }
            assertNotNull(findByTag(restored, "nusantara_quake_card"))
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun nusantaraHubExpandedRendersTwoColumnsWithSourceAttribution() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val storeOwner = FakeVmOwner()
            seedViewModel(storeOwner, NusantaraHubViewModel(EarthquakeRepository(FakeQuakeApi())))

            setTestContent(activity, isEnglish = false, vmOwner = storeOwner) {
                NusantaraHubScreen(
                    container = container,
                    isEnglish = false,
                    onOpenTool = {},
                    onBack = {}
                )
            }

            scrollUntilTag(activity, "nusantara_tool_CORE-15")
            val nodes = collectSemanticsNodes(activity.window.decorView)
            val c15 = findByTag(nodes, "nusantara_tool_CORE-15")
            val c16 = findByTag(nodes, "nusantara_tool_CORE-16")
            assertNotNull("Missing nusantara_tool_CORE-15 after scroll", c15)
            assertNotNull("Missing nusantara_tool_CORE-16 after scroll", c16)
            // In 2-column expanded layout, adjacent cards share the same top Y and differ horizontally.
            assertEquals(c15!!.boundsInRoot.top, c16!!.boundsInRoot.top, 4f)
            assertTrue(c16.boundsInRoot.left > c15.boundsInRoot.left)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun prayerTimesShowsPresetsScheduleHighlightAndErrorRetry() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val storeOwner = FakeVmOwner()
            val fakeApi = FakePrayerApi()
            val vm = PrayerTimesViewModel(PrayerTimesRepository(fakeApi))
            seedViewModel(storeOwner, vm)

            setTestContent(activity, isEnglish = true, vmOwner = storeOwner) {
                PrayerTimesScreen(container = container, isEnglish = true, onBack = {})
            }

            val initial = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "prayer_city_input") != null
            }
            assertNotNull(findByTag(initial, "prayer_city_input"))
            assertNotNull(findByTag(initial, "prayer_city_presets"))
            assertTrue(hasTextContaining(initial, "Choose a city to begin"))

            clickNode(findByTag(initial, "prayer_load_button")!!)
            val loaded = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "prayer_schedule_card") != null
            }
            assertNotNull(findByTag(loaded, "prayer_copy_schedule"))
            assertTrue(hasTextContaining(loaded, "KOTA YOGYAKARTA"))
            assertTrue(hasTextContaining(loaded, "Next prayer"))

            // Trigger an error state and verify retry recovery
            fakeApi.shouldFail = true
            clickNode(findByTag(loaded, "prayer_load_button")!!)
            val errored = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "prayer_retry_button") != null
            }
            assertNotNull(findByTag(errored, "prayer_retry_button"))
            assertNotNull("A failed refresh must keep the last schedule", vm.state.value.schedule)
            assertTrue("A failed refresh must mark the kept schedule as stale", vm.state.value.isStale)
            assertTrue(hasTextContaining(errored, "Showing the last successfully loaded data"))

            fakeApi.shouldFail = false
            clickNode(findByTag(errored, "prayer_retry_button")!!)
            val recovered = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "prayer_schedule_card") != null &&
                    findByTag(nodes, "prayer_retry_button") == null
            }
            assertNotNull(findByTag(recovered, "prayer_schedule_card"))
            assertFalse("A successful reload must clear the stale marker", vm.state.value.isStale)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun areaLookupShowsPromptEmptyClearAndCopyPostalCode() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val storeOwner = FakeVmOwner()
            val fakeApi = FakeAreaApi()
            seedViewModel(storeOwner, AreaLookupViewModel(AreaLookupRepository(fakeApi)))

            setTestContent(activity, isEnglish = true, vmOwner = storeOwner) {
                AreaLookupScreen(container = container, isEnglish = true, onBack = {})
            }

            val initial = collectSemanticsNodes(activity.window.decorView)
            val texts = initial.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
            assertTrue("Missing prompt text; texts=$texts", hasTextContaining(initial, "Search Indonesian addresses"))

            setText(findByTag(initial, "area_keyword_input")!!, "Soreang")
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "area_search_button")!!)

            val results = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "area_copy_40915") != null
            }
            assertTrue(hasTextContaining(results, "Soreang"))
            assertTrue(hasTextContaining(results, "40915"))
            assertTrue(hasTextContaining(results, "matching area(s) for"))

            val typed = collectSemanticsNodes(activity.window.decorView)
            setText(findByTag(typed, "area_keyword_input")!!, "Bandung")
            val changedQuery = waitUntilNodes(activity) { nodes ->
                hasTextContaining(nodes, "Results are still for")
            }
            assertNotNull(
                "Stale results must stay visible with their original keyword",
                findByTag(changedQuery, "area_copy_40915")
            )

            fakeApi.returnEmpty = true
            val beforeEmpty = collectSemanticsNodes(activity.window.decorView)
            clickNode(findByTag(beforeEmpty, "area_search_button")!!)
            val emptyNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "area_clear_button") != null
            }
            clickNode(findByTag(emptyNodes, "area_clear_button")!!)
            val cleared = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "area_clear_button") == null
            }
            assertTrue(hasTextContaining(cleared, "Search Indonesian addresses"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun weatherScreenRendersLocalizedConditionsAndRegionsOpensWeatherWithAdm4() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val regionsOwner = FakeVmOwner()
            val fakeRegionRepo = RegionRepository(FakeRegionApi())
            seedViewModel(regionsOwner, RegionsViewModel(fakeRegionRepo))

            var navigatedAdm4: String? = null
            setTestContent(activity, isEnglish = false, vmOwner = regionsOwner) {
                RegionsScreen(
                    container = container,
                    isEnglish = false,
                    onOpenWeather = { navigatedAdm4 = it },
                    onBack = {}
                )
            }

            // Drill Province -> Regency -> District -> Village
            val provNodes = waitUntilNodes(activity) { nodes ->
                hasTextContaining(nodes, "DKI JAKARTA")
            }
            clickNodeWithText(provNodes, "DKI JAKARTA")
            val regNodes = waitUntilNodes(activity) { nodes ->
                hasTextContaining(nodes, "KOTA JAKARTA PUSAT")
            }
            clickNodeWithText(regNodes, "KOTA JAKARTA PUSAT")
            val distNodes = waitUntilNodes(activity) { nodes ->
                hasTextContaining(nodes, "GAMBIR")
            }
            clickNodeWithText(distNodes, "GAMBIR")

            val openWeatherBtn = scrollUntilTag(activity, "regions_open_weather_31.71.01.1001")
            assertNotNull("Expected regions_open_weather_31.71.01.1001 after selecting district", openWeatherBtn)
            clickNode(openWeatherBtn!!)
            flushMainUi(activity)
            assertEquals("31.71.01.1001", navigatedAdm4)

            // Now open WeatherScreen directly with initialAdm4 and check English vs Indonesian labels
            val weatherOwnerEn = FakeVmOwner()
            val fakeWeatherRepo = WeatherRepository(FakeWeatherApi())
            seedViewModel(weatherOwnerEn, WeatherViewModel(fakeRegionRepo, fakeWeatherRepo))
            setTestContent(activity, isEnglish = true, vmOwner = weatherOwnerEn) {
                WeatherScreen(
                    container = container,
                    isEnglish = true,
                    onBack = {},
                    initialAdm4 = navigatedAdm4
                )
            }
            waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "weather_forecast_header") != null
            }
            val weatherEn = scrollUntilPredicate(activity) { nodes ->
                hasTextContaining(nodes, "Light Rain")
            }
            assertTrue(hasTextContaining(weatherEn, "Light Rain"))
            assertTrue(hasTextContaining(weatherEn, "Wind 6.4 km/h"))

            val weatherOwnerId = FakeVmOwner()
            seedViewModel(weatherOwnerId, WeatherViewModel(fakeRegionRepo, fakeWeatherRepo))
            setTestContent(activity, isEnglish = false, vmOwner = weatherOwnerId) {
                WeatherScreen(
                    container = container,
                    isEnglish = false,
                    onBack = {},
                    initialAdm4 = navigatedAdm4
                )
            }
            waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "weather_forecast_header") != null
            }
            val weatherId = scrollUntilPredicate(activity) { nodes ->
                hasTextContaining(nodes, "Hujan Ringan")
            }
            assertTrue(hasTextContaining(weatherId, "Hujan Ringan"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun earthquakeQuranFootballAndPesantrenSupportDetailStatesAndBackHandler() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // Earthquake Screen
            val quakeOwner = FakeVmOwner()
            seedViewModel(quakeOwner, EarthquakeViewModel(EarthquakeRepository(FakeQuakeApi())))
            setTestContent(activity, isEnglish = true, vmOwner = quakeOwner) {
                EarthquakeScreen(container = container, isEnglish = true, onBack = {})
            }
            val quakeNodes = waitUntilNodes(activity) { findByTag(it, "earthquake_latest_card") != null }
            val qTags = quakeNodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertNotNull("Missing earthquake_copy_coords; tags=$qTags", findByTag(quakeNodes, "earthquake_copy_coords"))
            assertNotNull("Missing earthquake_shakemap_image; tags=$qTags", findByTag(quakeNodes, "earthquake_shakemap_image"))

            // Quran Screen BackHandler
            val quranOwner = FakeVmOwner()
            seedViewModel(quranOwner, QuranViewModel(QuranRepository(FakeQuranApi())))
            setTestContent(activity, isEnglish = true, vmOwner = quranOwner) {
                QuranScreen(container = container, isEnglish = true, onBack = {})
            }
            val surahListNodes = waitUntilNodes(activity) { findByTag(it, "quran_surah_18") != null }
            clickNode(findByTag(surahListNodes, "quran_surah_18")!!)
            val verseNodes = waitUntilNodes(activity) { findByTag(it, "quran_copy_1") != null }
            assertNotNull(findByTag(verseNodes, "quran_audio_1"))
            activity.onBackPressedDispatcher.onBackPressed()
            val quranReturned = waitUntilNodes(activity) { findByTag(it, "quran_surah_18") != null }
            assertNull(findByTag(quranReturned, "quran_copy_1"))

            // Football Screen BackHandler & Seasons
            val footballOwner = FakeVmOwner()
            seedViewModel(footballOwner, FootballViewModel(FootballRepository(FakeFootballApi())))
            setTestContent(activity, isEnglish = true, vmOwner = footballOwner) {
                FootballScreen(container = container, isEnglish = true, onBack = {})
            }
            val leagueNodes = waitUntilNodes(activity) { findByTag(it, "football_league_idn.1") != null }
            clickNode(findByTag(leagueNodes, "football_league_idn.1")!!)
            val standingsNodes = waitUntilNodes(activity) { findByTag(it, "football_seasons") != null }
            assertTrue(hasTextContaining(standingsNodes, "Persib"))
            activity.onBackPressedDispatcher.onBackPressed()
            val footballReturned = waitUntilNodes(activity) { findByTag(it, "football_league_idn.1") != null }
            assertNull(findByTag(footballReturned, "football_seasons"))

            // Pesantren Screen BackHandler & Region Filter
            val pesantrenOwner = FakeVmOwner()
            seedViewModel(pesantrenOwner, PesantrenViewModel(PesantrenRepository(FakePesantrenApi())))
            setTestContent(activity, isEnglish = true, vmOwner = pesantrenOwner) {
                PesantrenScreen(container = container, isEnglish = true, onBack = {})
            }
            val pProvs = waitUntilNodes(activity) { findByTag(it, "pesantren_region_32") != null }
            clickNode(findByTag(pProvs, "pesantren_region_32")!!)
            val pRegs = waitUntilNodes(activity) { findByTag(it, "pesantren_region_3206") != null }
            assertNotNull(findByTag(pRegs, "pesantren_breadcrumbs"))
            activity.onBackPressedDispatcher.onBackPressed()
            val pBack = waitUntilNodes(activity) { findByTag(it, "pesantren_region_32") != null }
            assertNotNull(findByTag(pBack, "pesantren_region_32"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun doasSchoolsSundaneseTtsAndBackupRenderStatesAndScrollableCompact() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // DoasScreen expand/collapse
            val doasOwner = FakeVmOwner()
            seedViewModel(doasOwner, DoasViewModel(DoaRepository(FakeDoaApi())))
            setTestContent(activity, isEnglish = true, vmOwner = doasOwner) {
                DoasScreen(container = container, isEnglish = true, onBack = {})
            }
            val doas = waitUntilNodes(activity) { findByTag(it, "doa_card_1") != null }
            val dTags = doas.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            val doaCard = findByTag(doas, "doa_card_1")
            assertNotNull("Missing doa_card_1; tags=$dTags", doaCard)
            clickNode(doaCard!!)
            val doaExpanded = waitUntilNodes(activity) { findByTag(it, "doa_copy_1") != null }
            assertNotNull(findByTag(doaExpanded, "doa_copy_1"))

            // SchoolScreen min 3 chars validation + search
            val schoolOwner = FakeVmOwner()
            seedViewModel(schoolOwner, SchoolViewModel(SchoolRepository(FakeSchoolApi())))
            setTestContent(activity, isEnglish = true, vmOwner = schoolOwner) {
                SchoolScreen(container = container, isEnglish = true, onBack = {})
            }
            val schoolInit = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(schoolInit, "schools_min_chars_hint"))
            setText(findByTag(schoolInit, "schools_search")!!, "SMA")
            val schoolReady = waitUntilNodes(activity) { findByTag(it, "schools_min_chars_hint") == null }
            clickNode(findByTag(schoolReady, "schools_search_button")!!)
            val schoolResults = waitUntilNodes(activity) { findByTag(it, "school_copy_npsn_20104775") != null }
            assertTrue(hasTextContaining(schoolResults, "SMAN 1"))

            // SundaneseScreen presets & Undak Usuk Basa levels
            val sundaOwner = FakeVmOwner()
            seedViewModel(sundaOwner, SundaneseViewModel(SundaneseRepository(FakeSundaneseApi())))
            setTestContent(activity, isEnglish = true, vmOwner = sundaOwner) {
                SundaneseScreen(container = container, isEnglish = true, onBack = {})
            }
            val sundaInit = collectSemanticsNodes(activity.window.decorView)
            clickNodeWithText(sundaInit, "lebet")
            val sundaLoaded = waitUntilNodes(activity) { hasTextContaining(it, "abus, asup") }
            assertTrue(hasTextContaining(sundaLoaded, "Self (Sorangan)"))

            // TtsScreen letter count & presets
            val ttsOwner = FakeVmOwner()
            seedViewModel(ttsOwner, TtsViewModel(TtsRepository(FakeTtsApi())))
            setTestContent(activity, isEnglish = true, vmOwner = ttsOwner) {
                TtsScreen(container = container, isEnglish = true, onBack = {})
            }
            val ttsInit = collectSemanticsNodes(activity.window.decorView)
            clickNodeWithText(ttsInit, "tidak")
            val ttsLoaded = waitUntilNodes(activity) { hasTextContaining(it, "ENGGAK") }
            assertTrue(hasTextContaining(ttsLoaded, "6 letters"))

            // BackupScreen compact scrollable reachability
            setTestContent(activity, isEnglish = true) {
                BackupScreen(container = container, isEnglish = true, onBack = {})
            }
            val backupExport = scrollUntilTag(activity, "backup_export_button")
            val backupRestore = scrollUntilTag(activity, "backup_restore_button")
            assertNotNull(backupExport)
            assertNotNull(backupRestore)
        }
    }

    private fun samplePrayerSchedule() = PrayerSchedule(
        cityId = "1505",
        city = "KOTA YOGYAKARTA",
        region = "DI YOGYAKARTA",
        dateLabel = "Rabu, 25/11/2026",
        isoDate = "2026-11-25",
        imsak = "03:53",
        subuh = "04:03",
        terbit = "05:21",
        dhuha = "05:50",
        dzuhur = "11:42",
        ashar = "15:05",
        maghrib = "17:56",
        isya = "19:10"
    )

    private class FakeVmOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ViewModel> seedViewModel(owner: ViewModelStoreOwner, instance: T) {
        ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                override fun <V : ViewModel> create(modelClass: Class<V>): V = instance as V
            }
        )[instance::class.java]
    }

    private fun withMainActivity(block: (MainActivity) -> Unit) {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            flushMainUi(activity)
            block(activity)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            flushMainUi()
        }
    }

    private fun setTestContent(
        activity: MainActivity,
        isEnglish: Boolean,
        themeMode: ThemeMode = ThemeMode.LIGHT,
        vmOwner: ViewModelStoreOwner? = null,
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val targetLocale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("id")
            val localizedConfig = remember(baseConfig, targetLocale) {
                Configuration(baseConfig).apply { setLocale(targetLocale) }
            }
            val localizedResources = remember(baseContext, localizedConfig) {
                baseContext.createConfigurationContext(localizedConfig).resources
            }
            val localizedContext = remember(baseContext, localizedResources) {
                object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = localizedResources
                }
            }
            val resolvedOwner = vmOwner ?: LocalViewModelStoreOwner.current!!
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalResources provides localizedResources,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity,
                LocalViewModelStoreOwner provides resolvedOwner
            ) {
                HariMuTheme(
                    themeMode = themeMode,
                    isSystemDark = themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED,
                    dynamicColor = false,
                    content = content
                )
            }
        }
        flushMainUi(activity, frames = 6)
    }

    private fun flushMainUi(activity: MainActivity? = null, frames: Int = 4) {
        fun forceLayoutTree(view: View) {
            view.forceLayout()
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    forceLayoutTree(view.getChildAt(i))
                }
            }
        }
        repeat(frames) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            activity?.window?.decorView?.let { decor ->
                val dm = activity.resources.displayMetrics
                forceLayoutTree(decor)
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
                )
                decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
                decor.viewTreeObserver.dispatchOnPreDraw()
            }
        }
    }

    private fun waitUntilNodes(
        activity: MainActivity,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        repeat(60) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            Thread.sleep(20)
        }
        return collectSemanticsNodes(activity.window.decorView)
    }

    private fun scrollUntilTag(activity: MainActivity, tag: String): SemanticsNode? {
        val nodes = scrollUntilPredicate(activity) { findByTag(it, tag) != null }
        return findByTag(nodes, tag)
    }

    private fun scrollUntilPredicate(
        activity: MainActivity,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        repeat(20) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            val scrollable = nodes.lastOrNull { node ->
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                    node.config.getOrNull(SemanticsActions.ScrollBy) != null
            } ?: nodes.lastOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                ?: return nodes
            scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 900f)
            flushMainUi(activity, frames = 2)
        }
        return collectSemanticsNodes(activity.window.decorView)
    }

    private fun collectSemanticsNodes(rootView: View, mergingEnabled: Boolean = false): List<SemanticsNode> {
        val roots = mutableListOf<RootForTest>()
        fun walk(view: View) {
            if (view is RootForTest) roots += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(rootView)
        return roots.flatMap { root ->
            root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = mergingEnabled)
        }
    }

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }

    private fun hasTextContaining(nodes: List<SemanticsNode>, needle: String): Boolean =
        nodes.any { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.text.contains(needle, ignoreCase = true) } == true
        }

    private fun clickNodeWithText(nodes: List<SemanticsNode>, label: String) {
        val target = nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true &&
                node.config.getOrNull(SemanticsActions.OnClick) != null
        } ?: nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        }?.parent
        assertNotNull("Expected clickable node with label '$label'", target)
        clickNode(target!!)
    }

    private fun clickNode(node: SemanticsNode) {
        var current: SemanticsNode? = node
        while (current != null) {
            val action = current.config.getOrNull(SemanticsActions.OnClick)?.action
            if (action != null) {
                action.invoke()
                return
            }
            current = current.parent
        }
        error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
    }

    private fun setText(node: SemanticsNode, text: String) {
        val action = node.config.getOrNull(SemanticsActions.SetText)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no SetText action")
        action.invoke(AnnotatedString(text))
    }

    private inner class FakePrayerApi : PrayerTimesApi {
        var shouldFail = false
        override suspend fun findCities(keyword: String): List<PrayerCity> {
            if (shouldFail) throw IOException("offline")
            return listOf(PrayerCity("1505", "KOTA YOGYAKARTA"))
        }

        override suspend fun schedule(cityId: String, date: java.time.LocalDate): PrayerSchedule {
            if (shouldFail) throw IOException("offline")
            return samplePrayerSchedule()
        }
    }

    private class FakeAreaApi : AreaLookupApi {
        var returnEmpty = false
        override suspend fun search(keyword: String): List<AreaEntry> {
            if (returnEmpty) return emptyList()
            return listOf(
                AreaEntry(
                    negara = "Indonesia",
                    provinsi = "Jawa Barat",
                    kabkota = "Kabupaten Bandung",
                    kecamatan = "Soreang",
                    desakel = "Soreang",
                    kodepos = "40915"
                )
            )
        }
    }

    private class FakeQuakeApi : EarthquakeApi {
        override suspend fun latest() = Earthquake(
            date = "08 Nov 2026",
            time = "01:54:12 WIB",
            dateTime = "2026-11-07T18:54:12+00:00",
            coordinates = "-4.01,122.69",
            magnitude = "4.8",
            depth = "10 km",
            region = "20 km Timur Kendari",
            potential = "Tidak berpotensi tsunami",
            felt = "II Kendari",
            shakemap = "20261108015412.mmi.jpg"
        )

        override suspend fun recent() = listOf(latest())
    }

    private class FakeRegionApi : RegionApi {
        override suspend fun provinces() = listOf(Region("31", "DKI JAKARTA"))
        override suspend fun regencies(provinceId: String) = listOf(Region("31.71", "KOTA JAKARTA PUSAT"))
        override suspend fun districts(regencyId: String) = listOf(Region("31.71.01", "GAMBIR"))
        override suspend fun villages(districtId: String) = listOf(Region("31.71.01.1001", "GAMBIR"))
    }

    private class FakeWeatherApi : WeatherApi {
        override suspend fun forecast(adm4: String) = WeatherForecast(
            location = WeatherLocation(
                adm4 = adm4,
                province = "DKI Jakarta",
                city = "Jakarta Pusat",
                district = "Gambir",
                village = "Gambir"
            ),
            slots = listOf(
                WeatherSlot(
                    localTime = "2026-10-29 19:00:00",
                    temperatureC = 26,
                    humidityPercent = 92,
                    conditionId = "61",
                    conditionDescId = "Hujan Ringan",
                    conditionEn = "Light Rain",
                    windSpeed = "6.4",
                    iconUrl = ""
                )
            )
        )
    }

    private class FakeQuranApi : QuranApi {
        private val surah = QuranSurah(18, "Al-Kahf", "Goa", "The Cave", 110, "Makkiyyah")
        override suspend fun surahs() = listOf(surah)
        override suspend fun surah(number: Int) = QuranSurahDetail(
            surah = surah,
            verses = listOf(
                QuranVerse(
                    number = 1,
                    arabic = "الْحَمْدُ لِلَّهِ",
                    latin = "Alhamdu lillah",
                    translationId = "Segala puji bagi Allah",
                    translationEn = "Praise be to Allah",
                    audioUrl = "https://example.invalid/2141.mp3"
                )
            )
        )
    }

    private class FakeFootballApi : FootballApi {
        private val league = FootballLeague("idn.1", "Indonesian Liga 1", "Liga 1")
        override suspend fun leagues() = listOf(league)
        override suspend fun standings(leagueId: String, season: Int?) = FootballStandings(
            league = league.name,
            season = (season ?: 2026).toString(),
            rows = listOf(StandingRow(1, "Persib", 28, 18, 6, 4, "+24", 60))
        )
    }

    private class FakePesantrenApi : PesantrenApi {
        override suspend fun provinces() = listOf(PesantrenRegion("32", "Jawa Barat"))
        override suspend fun regencies(provinceId: String) = listOf(PesantrenRegion("3206", "Tasikmalaya"))
        override suspend fun pesantren(regencyId: String) = listOf(
            Pesantren("Al-Amanah", "510032061290", "Pasirhuni", "Aj. Sahid Idrus")
        )
    }

    private class FakeDoaApi : DoaApi {
        override suspend fun all() = listOf(
            Doa("1", "Doa sebelum tidur", "بِسْمِكَ", "Bismikallahumma", "Dengan menyebut nama Allah")
        )
    }

    private class FakeSchoolApi : SchoolApi {
        override suspend fun search(query: String, page: Int) = SchoolPage(
            schools = listOf(
                School("20104775", "SMAN 1 Jakarta", "SMA", "N", "Jl. Budi Utomo", "DKI Jakarta", "Jakarta Pusat", "Sawah Besar", "-6.16", "106.83")
            ),
            page = 1,
            perPage = 5,
            total = 1
        )
    }

    private class FakeSundaneseApi : SundaneseApi {
        override suspend fun lookup(word: String) = listOf(
            SundaneseEntry("lebet", "lebet", "abus, asup", "masuk", "enter")
        )
    }

    private class FakeTtsApi : TtsApi {
        override suspend fun answers(clue: String) = TtsResult(
            title = "Kunci jawaban TTS Tidak",
            total = 1,
            answers = listOf(TtsAnswer("ENGGAK", "Tidak", 5))
        )
    }
}
