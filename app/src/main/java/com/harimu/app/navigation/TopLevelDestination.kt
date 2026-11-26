package com.harimu.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.harimu.app.CatalogRoute
import com.harimu.app.FavoritesRoute
import com.harimu.app.HistoryRoute
import com.harimu.app.HomeRoute
import com.harimu.app.NusantaraRoute
import com.harimu.app.R
import com.harimu.app.SettingsRoute

/**
 * Destinations that belong to the persistent application frame.
 *
 * [showInNavigationBar] distinguishes the primary tabs (rendered as a bottom bar on compact
 * windows and as a rail on wider ones) from frame destinations that are opened from content,
 * such as the Nusantara hub. Adding a tab means adding one entry here: the bar, the rail and the
 * selected-state logic all read this list.
 */
enum class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val showInNavigationBar: Boolean = true,
    val providesOwnTopBar: Boolean = false
) {
    HOME(
        route = HomeRoute,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    CATALOG(
        route = CatalogRoute(),
        labelRes = R.string.nav_catalog,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        testTag = "nav_catalog",
        providesOwnTopBar = true
    ),
    FAVORITES(
        route = FavoritesRoute,
        labelRes = R.string.nav_favorites,
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.StarOutline,
        testTag = "nav_favorites"
    ),
    HISTORY(
        route = HistoryRoute,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    NUSANTARA(
        route = NusantaraRoute,
        labelRes = R.string.nav_nusantara,
        selectedIcon = Icons.Outlined.Public,
        unselectedIcon = Icons.Outlined.Public,
        testTag = "nav_nusantara",
        showInNavigationBar = false
    ),
    SETTINGS(
        route = SettingsRoute,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    );

    fun matches(destination: NavDestination?): Boolean = when (this) {
        HOME -> destination?.hasRoute<HomeRoute>() == true
        CATALOG -> destination?.hasRoute<CatalogRoute>() == true
        FAVORITES -> destination?.hasRoute<FavoritesRoute>() == true
        HISTORY -> destination?.hasRoute<HistoryRoute>() == true
        NUSANTARA -> destination?.hasRoute<NusantaraRoute>() == true
        SETTINGS -> destination?.hasRoute<SettingsRoute>() == true
    }

    companion object {
        val navigationItems: List<TopLevelDestination> = entries.filter { it.showInNavigationBar }

        fun of(destination: NavDestination?): TopLevelDestination? =
            entries.firstOrNull { it.matches(destination) }
    }
}
