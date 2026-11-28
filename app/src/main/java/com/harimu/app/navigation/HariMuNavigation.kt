package com.harimu.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.harimu.app.CatalogRoute
import com.harimu.app.FavoritesRoute
import com.harimu.app.HistoryRoute
import com.harimu.app.HomeRoute
import com.harimu.app.R
import com.harimu.app.SettingsRoute
import com.harimu.app.core.design.HariMuNavigationDefaults
import kotlin.reflect.KClass

const val SHELL_UP_BUTTON_TAG = "shell_up_button"

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    val labelRes: Int,
    val showInNavigationBar: Boolean = true,
    val providesOwnTopBar: Boolean = false
) {
    HOME(
        route = HomeRoute,
        routeClass = HomeRoute::class,
        icon = Icons.Filled.Home,
        labelRes = R.string.nav_home,
        showInNavigationBar = true,
        providesOwnTopBar = true
    ),
    CATALOG(
        route = CatalogRoute(),
        routeClass = CatalogRoute::class,
        icon = Icons.Filled.GridView,
        labelRes = R.string.nav_catalog,
        showInNavigationBar = true,
        providesOwnTopBar = true
    ),
    FAVORITES(
        route = FavoritesRoute,
        routeClass = FavoritesRoute::class,
        icon = Icons.Filled.Star,
        labelRes = R.string.nav_favorites,
        showInNavigationBar = true,
        providesOwnTopBar = false
    ),
    HISTORY(
        route = HistoryRoute,
        routeClass = HistoryRoute::class,
        icon = Icons.Filled.History,
        labelRes = R.string.nav_history,
        showInNavigationBar = true,
        providesOwnTopBar = false
    ),
    SETTINGS(
        route = SettingsRoute,
        routeClass = SettingsRoute::class,
        icon = Icons.Filled.Settings,
        labelRes = R.string.nav_settings,
        showInNavigationBar = true,
        providesOwnTopBar = false
    );

    companion object {
        fun of(destination: NavDestination?): TopLevelDestination? {
            if (destination == null) return null
            return entries.firstOrNull { top ->
                destination.hierarchy.any { it.hasRoute(top.routeClass) }
            }
        }
    }
}

fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun HariMuAppShell(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
    currentDestination: NavDestination?,
    showNavigation: Boolean,
    onSelectDestination: (TopLevelDestination) -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact ||
        heightSizeClass == WindowHeightSizeClass.Compact
    val barDestinations = TopLevelDestination.entries.filter { it.showInNavigationBar }

    if (useRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNavigation) {
                NavigationRail(
                    containerColor = HariMuNavigationDefaults.navigationRailContainerColor()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    barDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(dest.routeClass)
                        } == true
                        val label = stringResource(dest.labelRes)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelectDestination(dest) },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = label
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = HariMuNavigationDefaults.navigationRailItemColors(),
                            modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = topBar,
                    content = content
                )
            }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = topBar,
            bottomBar = {
                if (showNavigation) {
                    NavigationBar(
                        containerColor = HariMuNavigationDefaults.navigationBarContainerColor()
                    ) {
                        barDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(dest.routeClass)
                            } == true
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectDestination(dest) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = HariMuNavigationDefaults.navigationBarItemColors(),
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            content = content
        )
    }
}
