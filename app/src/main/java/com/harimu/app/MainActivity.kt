package com.harimu.app

import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.cloud.OAuthCallbackBus
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.HariMuTopBar
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.area.AreaLookupScreen
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.favorites.FavoritesScreen
import com.harimu.app.feature.history.HistoryScreen
import com.harimu.app.feature.home.HomeScreen
import com.harimu.app.feature.nusantara.DoasScreen
import com.harimu.app.feature.nusantara.EarthquakeScreen
import com.harimu.app.feature.nusantara.FootballScreen
import com.harimu.app.feature.nusantara.HeroesScreen
import com.harimu.app.feature.nusantara.NusantaraHubScreen
import com.harimu.app.feature.nusantara.PesantrenScreen
import com.harimu.app.feature.nusantara.QuranScreen
import com.harimu.app.feature.nusantara.RegionsScreen
import com.harimu.app.feature.nusantara.SchoolScreen
import com.harimu.app.feature.nusantara.SundaneseScreen
import com.harimu.app.feature.nusantara.TtsScreen
import com.harimu.app.feature.nusantara.VolcanoesScreen
import com.harimu.app.feature.nusantara.WeatherScreen
import com.harimu.app.feature.prayer.PrayerTimesScreen
import com.harimu.app.feature.settings.OpenSourceLicensesScreen
import com.harimu.app.feature.settings.SettingsScreen
import com.harimu.app.feature.studios.AudioForgeScreen
import com.harimu.app.feature.studios.AudioStudioScreen
import com.harimu.app.feature.studios.BackupScreen
import com.harimu.app.feature.studios.EqualizerScreen
import com.harimu.app.feature.studios.ImageForgeScreen
import com.harimu.app.feature.studios.ImageMatrixScreen
import com.harimu.app.feature.studios.KeyVaultScreen
import com.harimu.app.feature.studios.PdfStudioScreen
import com.harimu.app.feature.studios.PdfVaultScreen
import com.harimu.app.feature.studios.ScreenRecorderScreen
import com.harimu.app.feature.studios.UniversalConverterScreen
import com.harimu.app.feature.studios.VideoEditorScreen
import com.harimu.app.feature.studios.VideoForgeScreen
import com.harimu.app.feature.studios.VoiceRecorderScreen
import com.harimu.app.feature.studios.tabForPdfTool
import com.harimu.app.feature.toolscreen.ToolScreen
import com.harimu.app.navigation.HariMuAppShell
import com.harimu.app.navigation.SHELL_UP_BUTTON_TAG
import com.harimu.app.navigation.TopLevelDestination
import com.harimu.app.navigation.navigateToTopLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object FavoritesRoute
@Serializable data object SettingsRoute
@Serializable data class CatalogRoute(val groupName: String? = null, val initialQuery: String? = null)
@Serializable data class ToolRoute(val toolId: String, val jobId: String? = null)
@Serializable data object VoiceRecorderRoute
@Serializable data object ScreenRecorderRoute
@Serializable data object VideoEditorRoute
@Serializable data object VideoForgeRoute
@Serializable data object ImageForgeRoute
@Serializable data object ImageMatrixRoute
@Serializable data object AudioForgeRoute
@Serializable data object EqualizerRoute
@Serializable data object AudioStudioRoute
@Serializable data object PdfVaultRoute
@Serializable data class PdfStudioRoute(val initialTab: String? = null, val initialToolId: String? = null)
@Serializable data class UniversalConverterRoute(val initialToolId: String? = null)
@Serializable data object KeyVaultRoute
@Serializable data object BackupRoute
@Serializable data object PrayerTimesRoute
@Serializable data object AreaLookupRoute
@Serializable data object NusantaraRoute
@Serializable data object EarthquakeRoute
@Serializable data class BmkgWeatherRoute(val initialAdm4: String? = null)
@Serializable data object RegionsRoute
@Serializable data object HeroesRoute
@Serializable data object VolcanoesRoute
@Serializable data object DoasRoute
@Serializable data object SchoolRoute
@Serializable data object SundaneseRoute
@Serializable data object TtsRoute
@Serializable data object QuranRoute
@Serializable data object FootballRoute
@Serializable data object PesantrenRoute
@Serializable data object OpenSourceLicensesRoute

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HariMuApp).container
        handleOAuthIntent(intent)

        setContent {
            val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val windowSizeClass = calculateWindowSizeClass(this)

            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val targetLocale = if (settings.isEnglish) Locale.ENGLISH else Locale.forLanguageTag("id")
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

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity
            ) {
                HariMuTheme(
                    themeMode = settings.resolvedThemeMode(),
                    isSystemDark = isSystemInDarkTheme(),
                    dynamicColor = settings.dynamicColor
                ) {
                    HariMuRoot(
                        container = container,
                        settings = settings,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        heightSizeClass = windowSizeClass.heightSizeClass
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "harimu" && data.host == "oauth") {
            OAuthCallbackBus.publish(data.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HariMuRoot(
    container: AppContainer,
    settings: AppSettings,
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val favoriteIdsOrNull by container.workspace.favoriteIdsFlow.collectAsStateWithLifecycle(initialValue = null)
    val favoriteIds = favoriteIdsOrNull ?: emptySet()
    val favoritesLoaded = favoriteIdsOrNull != null
    val historyItemsOrNull by container.workspace.historyFlow.collectAsStateWithLifecycle(initialValue = null)
    val historyItems = historyItemsOrNull ?: emptyList()
    val historyLoaded = historyItemsOrNull != null
    val allJobs by container.jobManager.observeAllJobs().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeJobCount = remember(allJobs) { allJobs.count { ToolJobWorker.isActiveStatus(it.status) } }
    val attentionJobCount = remember(allJobs) { allJobs.count { ToolJobWorker.isAttentionStatus(it.status) } }
    val toggleFavorite: (String) -> Unit = remember(container) {
        { id -> scope.launch(Dispatchers.IO) { container.workspace.toggleFavorite(id) } }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    // The first frame has no back stack entry yet; show the frame as if Home were active.
    val currentTopLevel = if (navBackStackEntry == null) {
        TopLevelDestination.HOME
    } else {
        TopLevelDestination.of(currentDest)
    }

    fun navigateToStudio(route: DedicatedScreenRoute) {
        when (route) {
            DedicatedScreenRoute.VIDEO_EDITOR -> navController.navigate(VideoEditorRoute)
            DedicatedScreenRoute.VOICE_RECORDER -> navController.navigate(VoiceRecorderRoute)
            DedicatedScreenRoute.SCREEN_RECORDER -> navController.navigate(ScreenRecorderRoute)
            DedicatedScreenRoute.IMAGE_MATRIX -> navController.navigate(ImageMatrixRoute)
            DedicatedScreenRoute.EQUALIZER -> navController.navigate(EqualizerRoute)
            DedicatedScreenRoute.AUDIO_STUDIO -> navController.navigate(AudioStudioRoute)
            DedicatedScreenRoute.KEY_VAULT -> navController.navigate(KeyVaultRoute)
            DedicatedScreenRoute.WORKSPACE_BACKUP -> navController.navigate(BackupRoute)
            DedicatedScreenRoute.PRAYER_TIMES -> navController.navigate(PrayerTimesRoute)
            DedicatedScreenRoute.AREA_LOOKUP -> navController.navigate(AreaLookupRoute)
            DedicatedScreenRoute.EARTHQUAKE -> navController.navigate(EarthquakeRoute)
            DedicatedScreenRoute.BMKG_WEATHER -> navController.navigate(BmkgWeatherRoute())
            DedicatedScreenRoute.REGIONS -> navController.navigate(RegionsRoute)
            DedicatedScreenRoute.HEROES -> navController.navigate(HeroesRoute)
            DedicatedScreenRoute.VOLCANOES -> navController.navigate(VolcanoesRoute)
            DedicatedScreenRoute.DUAS -> navController.navigate(DoasRoute)
            DedicatedScreenRoute.SCHOOLS -> navController.navigate(SchoolRoute)
            DedicatedScreenRoute.SUNDANESE -> navController.navigate(SundaneseRoute)
            DedicatedScreenRoute.CROSSWORD -> navController.navigate(TtsRoute)
            DedicatedScreenRoute.QURAN -> navController.navigate(QuranRoute)
            DedicatedScreenRoute.FOOTBALL -> navController.navigate(FootballRoute)
            DedicatedScreenRoute.PESANTREN -> navController.navigate(PesantrenRoute)
            DedicatedScreenRoute.SETTINGS -> navController.navigateToTopLevel(TopLevelDestination.SETTINGS)
        }
    }

    fun openTool(tool: Tool, jobId: String? = null) {
        val dedicated = tool.dedicatedRoute
        if (dedicated != null) {
            navigateToStudio(dedicated)
        } else {
            navController.navigate(ToolRoute(tool.id, jobId))
        }
    }

    HariMuAppShell(
        widthSizeClass = widthSizeClass,
        heightSizeClass = heightSizeClass,
        currentDestination = currentDest,
        // Primary navigation belongs to the persistent tabs; the Nusantara hub is a frame
        // destination opened from content and keeps only its top bar with an up action.
        showNavigation = currentTopLevel?.showInNavigationBar == true,
        onSelectDestination = { destination -> navController.navigateToTopLevel(destination) },
        topBar = {
            if (currentTopLevel != null && !currentTopLevel.providesOwnTopBar) {
                val topBarTitleRes = when (currentTopLevel) {
                    TopLevelDestination.FAVORITES -> R.string.nav_favorites
                    TopLevelDestination.HISTORY -> R.string.nav_history
                    TopLevelDestination.SETTINGS -> R.string.nav_settings
                    else -> R.string.app_name
                }
                HariMuTopBar(
                    title = stringResource(topBarTitleRes),
                    onBack = if (!currentTopLevel.showInNavigationBar) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                    backTestTag = SHELL_UP_BUTTON_TAG
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        isEnglish = settings.isEnglish,
                        favoriteIds = favoriteIds,
                        recentToolIds = historyItems.map { it.toolId },
                        onOpenTool = { tool -> openTool(tool) },
                        onOpenGroup = { group ->
                            when (group) {
                                ToolGroup.NUSANTARA -> navController.navigate(NusantaraRoute)
                                ToolGroup.AUDIO -> navController.navigate(AudioForgeRoute)
                                ToolGroup.PDF -> navController.navigate(PdfVaultRoute)
                                ToolGroup.CONVERTER -> navController.navigate(UniversalConverterRoute())
                                else -> navController.navigate(CatalogRoute(group.name))
                            }
                        },
                        onToggleFavorite = toggleFavorite,
                        activeJobCount = activeJobCount,
                        attentionJobCount = attentionJobCount,
                        onOpenJobCenter = { navController.navigateToTopLevel(TopLevelDestination.HISTORY) },
                        isLoadingWorkspace = !favoritesLoaded || !historyLoaded,
                        onOpenCatalog = { navController.navigateToTopLevel(TopLevelDestination.CATALOG) },
                        onOpenFavorites = { navController.navigateToTopLevel(TopLevelDestination.FAVORITES) },
                        onSearchInCatalog = { query ->
                            navController.navigate(CatalogRoute(groupName = null, initialQuery = query))
                        }
                    )
                }
                composable<HistoryRoute> {
                    HistoryScreen(
                        container = container,
                        onOpenTool = { id, jid ->
                            ToolRegistry.findById(id)?.let { tool -> openTool(tool, jid) }
                        }
                    )
                }
                composable<FavoritesRoute> {
                    FavoritesScreen(
                        isEnglish = settings.isEnglish,
                        favoriteIds = favoriteIds,
                        onOpenTool = ::openTool,
                        onToggleFavorite = toggleFavorite,
                        isLoading = !favoritesLoaded,
                        onBrowseCatalog = { navController.navigateToTopLevel(TopLevelDestination.CATALOG) }
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        container = container,
                        settings = settings,
                        onOpenStudio = ::navigateToStudio,
                        onOpenInternalTool = { id -> navController.navigate(ToolRoute(id)) },
                        onOpenLicenses = { navController.navigate(OpenSourceLicensesRoute) }
                    )
                }
                composable<CatalogRoute> { entry ->
                    val args = entry.toRoute<CatalogRoute>()
                    val initialGroup = args.groupName?.let { name ->
                        ToolGroup.entries.firstOrNull { it.name == name }
                    }
                    CatalogScreen(
                        initialGroup = initialGroup,
                        isEnglish = settings.isEnglish,
                        favoriteIds = favoriteIds,
                        onOpenTool = ::openTool,
                        onToggleFavorite = toggleFavorite,
                        // Direct entries into a group or pre-filled query keep an explicit way back;
                        // the catalog tab itself is a root destination and has nothing to pop.
                        onBack = if (args.groupName != null || !args.initialQuery.isNullOrBlank()) {
                            { navController.popBackStack() }
                        } else {
                            null
                        },
                        onOpenAudioForge = { navController.navigate(AudioForgeRoute) },
                        onOpenImageForge = { navController.navigate(ImageForgeRoute) },
                        onOpenImageMatrix = { navController.navigate(ImageMatrixRoute) },
                        onOpenPdfVault = { navController.navigate(PdfVaultRoute) },
                        onOpenPdfStudio = { navController.navigate(PdfStudioRoute()) },
                        onOpenUniversalConverter = { navController.navigate(UniversalConverterRoute()) },
                        onOpenVideoForge = { navController.navigate(VideoForgeRoute) },
                        initialQuery = args.initialQuery.orEmpty()
                    )
                }
                composable<ToolRoute> { entry ->
                    val args = entry.toRoute<ToolRoute>()
                    ToolScreen(
                        toolId = args.toolId,
                        container = container,
                        isEnglish = settings.isEnglish,
                        isFavorite = args.toolId in favoriteIds,
                        onToggleFavorite = { toggleFavorite(args.toolId) },
                        onBack = { navController.popBackStack() },
                        onOpenPdfStudio = { tab, id ->
                            navController.navigate(PdfStudioRoute(initialTab = tab, initialToolId = id))
                        },
                        onOpenUniversalConverter = { id ->
                            navController.navigate(UniversalConverterRoute(initialToolId = id))
                        },
                        initialJobId = args.jobId,
                        onOpenJobCenter = { navController.navigateToTopLevel(TopLevelDestination.HISTORY) },
                        onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) }
                    )
                }
                composable<VoiceRecorderRoute> {
                    VoiceRecorderScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<ScreenRecorderRoute> {
                    ScreenRecorderScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<VideoEditorRoute> {
                    VideoEditorScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = { toolId -> navController.navigate(ToolRoute(toolId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<VideoForgeRoute> {
                    VideoForgeScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = ::openTool,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<ImageForgeRoute> {
                    ImageForgeScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = ::openTool,
                        onOpenImageMatrix = { navController.navigate(ImageMatrixRoute) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<ImageMatrixRoute> {
                    ImageMatrixScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<AudioForgeRoute> {
                    AudioForgeScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = ::openTool,
                        onOpenAudioStudio = { navController.navigate(AudioStudioRoute) },
                        onOpenEqualizer = { navController.navigate(EqualizerRoute) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<EqualizerRoute> {
                    EqualizerScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<AudioStudioRoute> {
                    AudioStudioScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = { toolId -> navController.navigate(ToolRoute(toolId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<PdfVaultRoute> {
                    PdfVaultScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = ::openTool,
                        onOpenPdfStudio = { tab, id ->
                            navController.navigate(PdfStudioRoute(initialTab = tab.name, initialToolId = id))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<PdfStudioRoute> { entry ->
                    val args = entry.toRoute<PdfStudioRoute>()
                    PdfStudioScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        initialToolId = args.initialToolId,
                        initialTabName = args.initialTab,
                        onOpenTool = { toolId -> navController.navigate(ToolRoute(toolId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<UniversalConverterRoute> { entry ->
                    val args = entry.toRoute<UniversalConverterRoute>()
                    UniversalConverterScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        initialToolId = args.initialToolId,
                        onOpenTool = ::openTool,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<KeyVaultRoute> {
                    KeyVaultScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<BackupRoute> {
                    BackupScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<PrayerTimesRoute> {
                    PrayerTimesScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<AreaLookupRoute> {
                    AreaLookupScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<NusantaraRoute> {
                    NusantaraHubScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenTool = { tool -> openTool(tool) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<EarthquakeRoute> {
                    EarthquakeScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<BmkgWeatherRoute> { entry ->
                    val args = entry.toRoute<BmkgWeatherRoute>()
                    WeatherScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        initialAdm4 = args.initialAdm4,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<RegionsRoute> {
                    RegionsScreen(
                        container = container,
                        isEnglish = settings.isEnglish,
                        onOpenWeather = { adm4 -> navController.navigate(BmkgWeatherRoute(initialAdm4 = adm4)) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<HeroesRoute> {
                    HeroesScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<VolcanoesRoute> {
                    VolcanoesScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<DoasRoute> {
                    DoasScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<SchoolRoute> {
                    SchoolScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<SundaneseRoute> {
                    SundaneseScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<TtsRoute> {
                    TtsScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<QuranRoute> {
                    QuranScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<FootballRoute> {
                    FootballScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<PesantrenRoute> {
                    PesantrenScreen(container, settings.isEnglish) { navController.popBackStack() }
                }
                composable<OpenSourceLicensesRoute> {
                    OpenSourceLicensesScreen(
                        isEnglish = settings.isEnglish,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
