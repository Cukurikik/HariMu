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
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.catalog.CatalogCapabilityFilter
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.catalog.CatalogSortOrder
import com.harimu.app.feature.catalog.filterAndSortCatalogTools
import com.harimu.app.feature.catalog.groupCatalogTools
import com.harimu.app.feature.favorites.FavoritesScreen
import com.harimu.app.feature.home.HomeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class HomeCatalogDiscoveryTest {

    @Test
    fun catalogDiscoveryHelpersCoverLargeCatalogFilteringSortingAndGrouping() {
        val all = filterAndSortCatalogTools(
            query = "",
            isEnglish = true,
            selectedGroup = null,
            capabilityFilter = CatalogCapabilityFilter.ALL,
            sortOrder = CatalogSortOrder.DEFAULT
        )
        assertEquals(182, all.size)

        val sections = groupCatalogTools(all)
        assertEquals(7, sections.size)
        assertEquals(182, sections.sumOf { it.tools.size })

        val online = filterAndSortCatalogTools(
            query = "",
            isEnglish = true,
            selectedGroup = null,
            capabilityFilter = CatalogCapabilityFilter.ONLINE
        )
        assertTrue("Expected online tools to require network", online.isNotEmpty() && online.all { it.requiresNetwork })

        val onDevice = filterAndSortCatalogTools(
            query = "",
            isEnglish = true,
            selectedGroup = null,
            capabilityFilter = CatalogCapabilityFilter.ON_DEVICE
        )
        assertTrue(
            "Expected on-device tools to require neither network nor downloadable model",
            onDevice.isNotEmpty() && onDevice.all { !it.requiresNetwork && it.requiresModel == null }
        )

        val localModel = filterAndSortCatalogTools(
            query = "",
            isEnglish = true,
            selectedGroup = null,
            capabilityFilter = CatalogCapabilityFilter.LOCAL_MODEL
        )
        val expectedLocalModelCount = ToolRegistry.allTools.count { it.requiresModel != null }
        assertEquals(expectedLocalModelCount, localModel.size)
        assertTrue("Expected local-model tools to specify requiresModel", localModel.all { it.requiresModel != null })

        val sorted = filterAndSortCatalogTools(
            query = "",
            isEnglish = true,
            selectedGroup = ToolGroup.VIDEO,
            sortOrder = CatalogSortOrder.TITLE_ASC
        )
        assertEquals(ToolRegistry.countByGroup(ToolGroup.VIDEO), sorted.size)
        val titles = sorted.map { it.title(true).lowercase() }
        assertEquals(titles.sorted(), titles)

        // Multi-token cross-field search and exact-ID priority
        val exactId = ToolRegistry.search("VID-01", isEnglish = true)
        assertEquals("VID-01", exactId.first().id)

        val multiToken = ToolRegistry.search("trim video", isEnglish = true)
        assertTrue(multiToken.any { it.id == "VID-01" })

        val emptySearch = filterAndSortCatalogTools(
            query = "zzz_nonexistent_tool_999",
            isEnglish = true,
            selectedGroup = null
        )
        assertTrue(emptySearch.isEmpty())
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun workspaceFavoritesPersistToggleOrderAndUpdateHomeCatalogFavoritesUi() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            runBlocking {
                // Clean slate
                container.workspace.favoriteIdsFlow.first().toList().forEach { id ->
                    container.workspace.toggleFavorite(id)
                }
                container.workspace.toggleFavorite("   ") // ignored safely
                assertTrue(container.workspace.favoriteIdsFlow.first().isEmpty())

                container.workspace.toggleFavorite("VID-01")
                Thread.sleep(5)
                container.workspace.toggleFavorite("IMG-06")
                val ids = container.workspace.favoriteIdsFlow.first()
                assertEquals(listOf("IMG-06", "VID-01"), ids.toList())
            }

            // Launch root activity and verify Home displays Favorites section in order
            val homeNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_fav_card_IMG-06") != null &&
                    findByTag(nodes, "home_fav_card_VID-01") != null
            }
            assertNotNull(findByTag(homeNodes, "home_favorites_see_all"))

            // Unfavorite IMG-06 directly from Home quick card
            clickNode(findByTag(homeNodes, "home_fav_toggle_IMG-06")!!)
            val afterHomeUnfav = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_fav_card_IMG-06") == null &&
                    findByTag(nodes, "home_fav_card_VID-01") != null
            }
            assertNull(findByTag(afterHomeUnfav, "home_fav_card_IMG-06"))

            // Navigate to Favorites tab and verify VID-01 selected state, then unfavorite to reach empty CTA
            clickNode(findByTag(afterHomeUnfav, "nav_favorites")!!)
            val favNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "tool_card_VID-01") != null
            }
            val favBtn = findByTag(favNodes, "fav_button_VID-01")!!
            assertEquals(true, favBtn.config.getOrNull(SemanticsProperties.Selected))

            clickNode(favBtn)
            val emptyFavNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "favorites_browse_catalog_button") != null
            }
            assertNotNull(findByTag(emptyFavNodes, "favorites_browse_catalog_button"))

            // Empty Favorites CTA navigates to Catalog
            clickNode(findByTag(emptyFavNodes, "favorites_browse_catalog_button")!!)
            val catalogNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_search_input") != null
            }
            assertNotNull(findByTag(catalogNodes, "catalog_search_input"))

            // Favorite PDF-01 inside Catalog via search and verify selected state flips to true
            setSearchText(catalogNodes, "catalog_search_input", "PDF-01")
            val pdfSearchNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "fav_button_PDF-01") != null
            }
            val pdfFavBtn = findByTag(pdfSearchNodes, "fav_button_PDF-01")!!
            assertEquals(false, pdfFavBtn.config.getOrNull(SemanticsProperties.Selected))
            clickNode(pdfFavBtn)

            val pdfFavoritedNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "fav_button_PDF-01")?.config?.getOrNull(SemanticsProperties.Selected) == true
            }
            assertEquals(
                true,
                findByTag(pdfFavoritedNodes, "fav_button_PDF-01")?.config?.getOrNull(SemanticsProperties.Selected)
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun catalogSearchFilterSortAndEmptyResetWorkAcrossLargeCatalog() {
        withMainActivity { activity ->
            var openedTool: Tool? = null
            setTestContent(activity, isEnglish = true) {
                CatalogScreen(
                    initialGroup = null,
                    isEnglish = true,
                    favoriteIds = setOf("VID-01"),
                    onOpenTool = { openedTool = it },
                    onToggleFavorite = {}
                )
            }

            // Grouped view on large 182-tool catalog shows first section and summary
            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(initialNodes, "catalog_section_CORE"))
            assertTrue(
                collectAllText(initialNodes).any { it.contains("182") }
            )

            // Filter by Online capability
            clickNode(findByTag(initialNodes, "catalog_cap_ONLINE")!!)
            val onlineNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_clear_filters_inline") != null &&
                    findByTag(nodes, "catalog_section_CORE") != null
            }
            assertNotNull(findByTag(onlineNodes, "catalog_clear_filters_inline"))

            // Toggle A-Z sort
            clickNode(findByTag(onlineNodes, "catalog_sort_toggle")!!)
            flushMainUi(activity)

            // Search for non-existent tool -> EmptyState with Reset Filters button
            val afterSortNodes = collectSemanticsNodes(activity.window.decorView)
            setSearchText(afterSortNodes, "catalog_search_input", "zzz_unknown_query")
            val emptyNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_reset_filters_button") != null
            }
            assertNotNull(findByTag(emptyNodes, "catalog_reset_filters_button"))

            // Clicking Reset Filters restores full catalog
            clickNode(findByTag(emptyNodes, "catalog_reset_filters_button")!!)
            val resetNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_section_CORE") != null &&
                    findByTag(nodes, "catalog_clear_filters_inline") == null
            }
            assertNotNull(findByTag(resetNodes, "catalog_section_CORE"))

            // Search by ID and open tool
            setSearchText(resetNodes, "catalog_search_input", "IMG-06")
            val imgNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "tool_card_IMG-06") != null
            }
            clickNode(findByTag(imgNodes, "tool_card_IMG-06")!!)
            flushMainUi(activity)
            assertEquals("IMG-06", openedTool?.id)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun homeEmptySearchClearsAndDiscoveryNavigationWorks() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            runBlocking {
                container.workspace.favoriteIdsFlow.first().toList().forEach { id ->
                    container.workspace.toggleFavorite(id)
                }
            }
            val initialNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_search_input") != null &&
                    findByTag(nodes, "home_favorites_empty") != null
            }
            assertNotNull(findByTag(initialNodes, "home_open_catalog_button"))

            // Search empty state on Home offers Clear Search CTA
            setSearchText(initialNodes, "home_search_input", "missing_tool_xyz")
            val emptySearchNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_clear_search_button") != null
            }
            clickNode(findByTag(emptySearchNodes, "home_clear_search_button")!!)
            val restoredHomeNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_recent_section") != null
            }
            assertNotNull(findByTag(restoredHomeNodes, "home_recent_section"))

            // Open Catalog via Home discovery header CTA
            clickNode(findByTag(restoredHomeNodes, "home_open_catalog_button")!!)
            val catalogNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_search_input") != null
            }
            assertNotNull(findByTag(catalogNodes, "catalog_search_input"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun activityRecreationRetainsCatalogSearchAndFilters() {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            flushMainUi(activity)

            // Navigate to Catalog tab, filter to VIDEO, and enter search query
            val homeNodes = collectSemanticsNodes(activity.window.decorView)
            clickNode(findByTag(homeNodes, "nav_catalog")!!)
            val catalogNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "filter_VIDEO") != null
            }
            clickNode(findByTag(catalogNodes, "filter_VIDEO")!!)
            flushMainUi(activity, frames = 6)

            val videoNodes = collectSemanticsNodes(activity.window.decorView)
            setSearchText(videoNodes, "catalog_search_input", "Trim")
            val trimNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "tool_card_VID-01") != null
            }
            assertNotNull(findByTag(trimNodes, "tool_card_VID-01"))

            // Recreate activity (simulating config change / state restoration)
            controller.recreate()
            val recreated = controller.get()
            flushMainUi(recreated, frames = 8)

            val restoredNodes = waitUntilNodes(recreated) { nodes ->
                findByTag(nodes, "catalog_search_input") != null &&
                    findByTag(nodes, "tool_card_VID-01") != null
            }
            assertNotNull("Expected Catalog search & filter state retained across recreation", findByTag(restoredNodes, "tool_card_VID-01"))
            val searchText = findByTag(restoredNodes, "catalog_search_input")
                ?.config?.getOrNull(SemanticsProperties.EditableText)?.text
            assertEquals("Trim", searchText)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            flushMainUi()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun favoritesScreenShowsOrderedToolsAndFiltersWhenPopulated() {
        withMainActivity { activity ->
            setTestContent(activity, isEnglish = true) {
                FavoritesScreen(
                    isEnglish = true,
                    favoriteIds = linkedSetOf("PDF-01", "VID-01"),
                    onOpenTool = {},
                    onToggleFavorite = {},
                    isLoading = false,
                    onBrowseCatalog = {}
                )
            }
            val nodes = collectSemanticsNodes(activity.window.decorView)
            val pdfCard = findByTag(nodes, "tool_card_PDF-01")
            val vidCard = findByTag(nodes, "tool_card_VID-01")
            assertNotNull(pdfCard)
            assertNotNull(vidCard)
            assertTrue(
                "Expected Room order (PDF-01 before VID-01) preserved",
                pdfCard!!.boundsInRoot.top < vidCard!!.boundsInRoot.top
            )

            // Filter by VIDEO group inside Favorites
            clickNode(findByTag(nodes, "favorites_filter_VIDEO")!!)
            val filteredNodes = waitUntilNodes(activity) { current ->
                findByTag(current, "tool_card_PDF-01") == null &&
                    findByTag(current, "tool_card_VID-01") != null
            }
            assertNull(findByTag(filteredNodes, "tool_card_PDF-01"))
            assertNotNull(findByTag(filteredNodes, "tool_card_VID-01"))
        }
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
        themeMode: ThemeMode = ThemeMode.SYSTEM,
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
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalResources provides localizedResources,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity
            ) {
                HariMuTheme(
                    themeMode = themeMode,
                    isSystemDark = themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED,
                    dynamicColor = false,
                    content = content
                )
            }
        }
        flushMainUi(activity)
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
        repeat(50) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            Thread.sleep(20)
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

    private fun collectAllText(nodes: List<SemanticsNode>): List<String> =
        nodes.flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }

    private fun setSearchText(nodes: List<SemanticsNode>, tag: String, text: String) {
        val node = findByTag(nodes, tag)
            ?: error("Search input node $tag not found")
        val setText = node.config.getOrNull(SemanticsActions.SetText)?.action
            ?: error("Search input node $tag has no SetText action")
        setText.invoke(AnnotatedString(text))
    }
}
