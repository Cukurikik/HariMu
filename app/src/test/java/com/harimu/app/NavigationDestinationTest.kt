package com.harimu.app

import com.harimu.app.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDestinationTest {

    @Test
    fun primaryNavigationKeepsFiveUniqueTabs() {
        val items = TopLevelDestination.navigationItems
        assertEquals(5, items.size)
        assertEquals(
            listOf("nav_home", "nav_catalog", "nav_favorites", "nav_history", "nav_settings"),
            items.map { it.testTag }
        )
        assertEquals(
            "Every primary tab must map to a distinct route",
            items.size,
            items.map { it.route::class }.distinct().size
        )
    }

    @Test
    fun nusantaraStaysReachableWithoutDuplicatingATab() {
        val nusantara = TopLevelDestination.NUSANTARA
        assertFalse("The Nusantara hub is opened from content, not from the primary bar", nusantara.showInNavigationBar)
        assertTrue(TopLevelDestination.navigationItems.none { it == nusantara })
    }

    @Test
    fun onlyTheCatalogTabProvidesItsOwnTopBar() {
        val ownBarTabs = TopLevelDestination.navigationItems.filter { it.providesOwnTopBar }
        assertEquals(listOf(TopLevelDestination.CATALOG), ownBarTabs)
    }
}
