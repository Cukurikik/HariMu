package com.harimu.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination
import com.harimu.app.core.design.HariMuNavigationDefaults

const val SHELL_BOTTOM_NAVIGATION_TAG = "shell_bottom_navigation"
const val SHELL_NAVIGATION_RAIL_TAG = "shell_navigation_rail"
const val SHELL_UP_BUTTON_TAG = "shell_up_button"

/**
 * A navigation rail needs horizontal room, or a short window where a bottom bar would eat the
 * remaining vertical space (a phone in landscape). Compact portrait phones keep the bottom bar.
 */
internal fun shouldUseNavigationRail(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass
): Boolean =
    widthSizeClass != WindowWidthSizeClass.Compact || heightSizeClass == WindowHeightSizeClass.Compact

/**
 * Application frame shared by every destination: adaptive primary navigation plus a scaffold that
 * owns window insets and the app-level top bar.
 *
 * Detail screens keep their own top bar and are expected to be opened with [showNavigation]
 * disabled through [TopLevelDestination.of] returning null.
 */
@Composable
fun HariMuAppShell(
    widthSizeClass: WindowWidthSizeClass,
    heightSizeClass: WindowHeightSizeClass,
    currentDestination: NavDestination?,
    showNavigation: Boolean,
    onSelectDestination: (TopLevelDestination) -> Unit,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val useRail = shouldUseNavigationRail(widthSizeClass, heightSizeClass)
    val selected = TopLevelDestination.of(currentDestination)

    Row(modifier = Modifier.fillMaxSize()) {
        if (showNavigation && useRail) {
            HariMuNavigationRail(
                selected = selected,
                onSelect = onSelectDestination,
                // A short window (landscape phone) cannot fit five labeled rail items, so the
                // rail keeps icons only and moves the label into the icon's contentDescription.
                showLabels = heightSizeClass != WindowHeightSizeClass.Compact
            )
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = topBar,
            bottomBar = {
                if (showNavigation && !useRail) {
                    HariMuNavigationBar(selected = selected, onSelect = onSelectDestination)
                }
            },
            content = content
        )
    }
}

@Composable
private fun HariMuNavigationBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit
) {
    NavigationBar(
        modifier = Modifier.testTag(SHELL_BOTTOM_NAVIGATION_TAG),
        containerColor = HariMuNavigationDefaults.navigationBarContainerColor()
    ) {
        TopLevelDestination.navigationItems.forEach { destination ->
            val isSelected = destination == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                colors = HariMuNavigationDefaults.navigationBarItemColors(),
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}

@Composable
private fun HariMuNavigationRail(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    showLabels: Boolean
) {
    NavigationRail(
        modifier = Modifier.testTag(SHELL_NAVIGATION_RAIL_TAG),
        containerColor = HariMuNavigationDefaults.navigationRailContainerColor()
    ) {
        TopLevelDestination.navigationItems.forEach { destination ->
            val isSelected = destination == selected
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                colors = HariMuNavigationDefaults.navigationRailItemColors(),
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = if (showLabels) null else stringResource(destination.labelRes)
                    )
                },
                label = if (showLabels) {
                    {
                        Text(
                            text = stringResource(destination.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.testTag(destination.testTag)
            )
        }
    }
}
