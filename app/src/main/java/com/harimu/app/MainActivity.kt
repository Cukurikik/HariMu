package com.harimu.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.favorites.FavoritesScreen
import com.harimu.app.feature.history.HistoryScreen
import com.harimu.app.feature.home.HomeScreen
import com.harimu.app.feature.settings.SettingsScreen
import com.harimu.app.feature.studios.BackupScreen
import com.harimu.app.feature.studios.EqualizerScreen
import com.harimu.app.feature.studios.ImageMatrixScreen
import com.harimu.app.feature.studios.KeyVaultScreen
import com.harimu.app.feature.studios.AudioStudioProScreen
import com.harimu.app.feature.studios.ProVideoEditorScreen
import com.harimu.app.feature.studios.VoiceRecorderScreen
import com.harimu.app.feature.toolscreen.ToolScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object FavoritesRoute
@Serializable data object SettingsRoute
@Serializable data class CatalogRoute(val groupName: String? = null)
@Serializable data class ToolRoute(val toolId: String)
@Serializable data object VoiceRecorderRoute
@Serializable data object ImageMatrixRoute
@Serializable data object EqualizerRoute
@Serializable data object KeyVaultRoute
@Serializable data object BackupRoute
@Serializable data object ProVideoEditorRoute
@Serializable data object AudioStudioProRoute

private data class TopLevelNav(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HariMuApp).container

        setContent {
            val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val windowSizeClass = calculateWindowSizeClass(this)

            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val targetLocale = if (settings.isEnglish) Locale.ENGLISH else Locale("id")
            val localizedConfig = remember(baseConfig, targetLocale) {
                Configuration(baseConfig).apply { setLocale(targetLocale) }
            }
            val localizedContext = remember(baseContext, localizedConfig) {
                baseContext.createConfigurationContext(localizedConfig)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext
            ) {
                HariMuTheme(
                    themeMode = settings.resolvedThemeMode(),
                    isSystemDark = isSystemInDarkTheme(),
                    dynamicColor = settings.dynamicColor
                ) {
                    HariMuRoot(
                        container = container,
                        settings = settings,
                        widthSizeClass = windowSizeClass.widthSizeClass
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HariMuRoot(
    container: AppContainer,
    settings: AppSettings,
    widthSizeClass: WindowWidthSizeClass
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val favoriteIds by container.workspace.favoriteIdsFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val historyItems by container.workspace.historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val topLevels = listOf(
        TopLevelNav(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        TopLevelNav(HistoryRoute, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History, "nav_history"),
        TopLevelNav(FavoritesRoute, R.string.nav_favorites, Icons.Filled.Star, Icons.Outlined.StarOutline, "nav_favorites"),
        TopLevelNav(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    val isTopLevel = currentDest?.let { d ->
        d.hasRoute<HomeRoute>() || d.hasRoute<HistoryRoute>() || d.hasRoute<FavoritesRoute>() || d.hasRoute<SettingsRoute>()
    } ?: true

    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    fun navigateToStudio(route: DedicatedScreenRoute) {
        when (route) {
            DedicatedScreenRoute.VOICE_RECORDER -> navController.navigate(VoiceRecorderRoute)
            DedicatedScreenRoute.IMAGE_MATRIX -> navController.navigate(ImageMatrixRoute)
            DedicatedScreenRoute.EQUALIZER -> navController.navigate(EqualizerRoute)
            DedicatedScreenRoute.KEY_VAULT -> navController.navigate(KeyVaultRoute)
            DedicatedScreenRoute.WORKSPACE_BACKUP -> navController.navigate(BackupRoute)
            DedicatedScreenRoute.PRO_VIDEO_EDITOR -> navController.navigate(ProVideoEditorRoute)
            DedicatedScreenRoute.AUDIO_STUDIO_PRO -> navController.navigate(AudioStudioProRoute)
            DedicatedScreenRoute.SETTINGS -> navController.navigate(SettingsRoute)
        }
    }

    fun openTool(tool: Tool) {
        val dedicated = tool.dedicatedRoute
        if (dedicated != null) {
            navigateToStudio(dedicated)
        } else {
            navController.navigate(ToolRoute(tool.id))
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail && isTopLevel) {
            NavigationRail {
                topLevels.forEach { item ->
                    val selected = when (item.route) {
                        is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                        is HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                        is FavoritesRoute -> currentDest?.hasRoute<FavoritesRoute>() == true
                        is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                        else -> false
                    }
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(HomeRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                        modifier = Modifier.testTag(item.tag)
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (isTopLevel) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (!useRail && isTopLevel) {
                    NavigationBar {
                        topLevels.forEach { item ->
                            val selected = when (item.route) {
                                is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                is HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                                is FavoritesRoute -> currentDest?.hasRoute<FavoritesRoute>() == true
                                is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(HomeRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            isEnglish = settings.isEnglish,
                            favoriteIds = favoriteIds,
                            recentToolIds = historyItems.map { it.toolId },
                            onOpenTool = ::openTool,
                            onOpenGroup = { group -> navController.navigate(CatalogRoute(group.name)) },
                            onToggleFavorite = { id ->
                                scope.launch(Dispatchers.IO) { container.workspace.toggleFavorite(id) }
                            }
                        )
                    }
                    composable<HistoryRoute> {
                        HistoryScreen(container = container)
                    }
                    composable<FavoritesRoute> {
                        FavoritesScreen(
                            isEnglish = settings.isEnglish,
                            favoriteIds = favoriteIds,
                            onOpenTool = ::openTool,
                            onToggleFavorite = { id ->
                                scope.launch(Dispatchers.IO) { container.workspace.toggleFavorite(id) }
                            }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            container = container,
                            settings = settings,
                            onOpenStudio = ::navigateToStudio,
                            onOpenInternalTool = { id -> navController.navigate(ToolRoute(id)) }
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
                            onToggleFavorite = { id ->
                                scope.launch(Dispatchers.IO) { container.workspace.toggleFavorite(id) }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<ToolRoute> { entry ->
                        val args = entry.toRoute<ToolRoute>()
                        ToolScreen(
                            toolId = args.toolId,
                            container = container,
                            isEnglish = settings.isEnglish,
                            isFavorite = args.toolId in favoriteIds,
                            onToggleFavorite = {
                                scope.launch(Dispatchers.IO) { container.workspace.toggleFavorite(args.toolId) }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<VoiceRecorderRoute> {
                        VoiceRecorderScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                    composable<ImageMatrixRoute> {
                        ImageMatrixScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                    composable<EqualizerRoute> {
                        EqualizerScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                    composable<KeyVaultRoute> {
                        KeyVaultScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                    composable<BackupRoute> {
                        BackupScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                    composable<ProVideoEditorRoute> {
                        ProVideoEditorScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                    composable<AudioStudioProRoute> {
                        AudioStudioProScreen(container, settings.isEnglish) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}
