package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.history.HistoryScreen
import com.harimu.app.feature.home.HomeScreen
import com.harimu.app.feature.settings.OpenSourceLicensesScreen
import com.harimu.app.feature.settings.SettingsScreen
import com.harimu.app.feature.studios.AudioStudioScreen
import com.harimu.app.feature.toolscreen.PerspectivePointsCard
import com.harimu.app.feature.toolscreen.ToolScreen
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
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class HariMuUiAndNavigationTest {

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun mainActivityCompactLaunchesAndNavigatesBetweenTopLevelAndLicenses() {
        withMainActivity { activity ->
            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(initialNodes, "shell_bottom_navigation"))
            assertNull(findByTag(initialNodes, "shell_navigation_rail"))
            assertNotNull(findByTag(initialNodes, "nav_home"))
            assertNotNull(findByTag(initialNodes, "nav_catalog"))
            assertNotNull(findByTag(initialNodes, "nav_history"))
            assertNotNull(findByTag(initialNodes, "nav_favorites"))
            assertNotNull(findByTag(initialNodes, "nav_settings"))
            assertNotNull(findByTag(initialNodes, "home_search_input"))

            // Navigate to Settings via bottom NavigationBar item
            clickNode(findByTag(initialNodes, "nav_settings")!!)
            flushMainUi(activity, frames = 12)
            val settingsNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "language_switch") != null
            }
            val settingsTags = settingsNodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertNotNull("Expected language_switch after clicking nav_settings; tags=$settingsTags", findByTag(settingsNodes, "language_switch"))

            val licensesBtn = scrollUntilTag(activity, "open_source_licenses_button")
            val afterScrollTags = collectSemanticsNodes(activity.window.decorView)
                .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertNotNull("Expected open_source_licenses_button in SettingsScreen; tags=$afterScrollTags", licensesBtn)

            // Navigate into OpenSourceLicensesScreen and verify back navigation returns
            clickNode(licensesBtn!!)
            flushMainUi(activity, frames = 12)
            val licenseNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "open_source_licenses_list") != null
            }
            assertNotNull(findByTag(licenseNodes, "open_source_licenses_list"))
            val mergedLicenseNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val backNode = findByTag(mergedLicenseNodes, "licenses_back_button")
            assertNotNull(backNode)
            val backDesc = backNode!!.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            assertFalse("Expected non-blank contentDescription on licenses_back_button", backDesc.isNullOrBlank())

            clickNode(backNode)
            flushMainUi(activity, frames = 12)
            val restored = scrollUntilTag(activity, "open_source_licenses_button")
            assertNotNull(restored)
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun mainActivityExpandedUsesNavigationRailWithAccessibleTouchTargets() {
        withMainActivity { activity ->
            val density = activity.resources.displayMetrics.density
            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(nodes, "shell_navigation_rail"))
            assertNull(findByTag(nodes, "shell_bottom_navigation"))
            listOf("nav_home", "nav_catalog", "nav_history", "nav_favorites", "nav_settings").forEach { tag ->
                val node = findByTag(nodes, tag)
                assertNotNull("Expected NavigationRail item $tag", node)
                val bounds = node!!.boundsInRoot
                val heightDp = bounds.height / density
                assertTrue("Touch target height for $tag was ${heightDp}dp", heightDp >= 47.5f)
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun homeAndCatalogRenderGroupCardsAndFilterChipsSafely() {
        withMainActivity { activity ->
            var openedGroup: ToolGroup? = null
            setTestContent(activity, isEnglish = true) {
                HomeScreen(
                    isEnglish = true,
                    favoriteIds = setOf("IMG-06"),
                    recentToolIds = emptyList(),
                    onOpenTool = {},
                    onOpenGroup = { openedGroup = it },
                    onToggleFavorite = {}
                )
            }
            val groupCard = scrollUntilTag(activity, "group_card_IMAGE")
            assertNotNull("Expected group_card_IMAGE in HomeScreen", groupCard)
            clickNode(groupCard!!)
            flushMainUi()
            assertEquals(ToolGroup.IMAGE, openedGroup)

            setTestContent(activity, isEnglish = false) {
                CatalogScreen(
                    initialGroup = ToolGroup.IMAGE,
                    isEnglish = false,
                    favoriteIds = emptySet(),
                    onOpenTool = {},
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            val catalogNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(catalogNodes, "catalog_search_input"))
            assertNotNull(findByTag(catalogNodes, "filter_all"))
            assertNotNull(findByTag(catalogNodes, "filter_IMAGE"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun historyLongTitleKeepsDeleteTouchTargetWithinViewport() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val sampleFile = File(activity.cacheDir, "sample_history_test.txt").apply {
                writeText("ok")
            }
            try {
                runBlocking {
                    container.workspace.clearHistory()
                    container.workspace.recordHistory(
                        toolId = "IMG-06",
                        toolTitle = "Koreksi Perspektif Dokumen Empat Sudut Resolusi Tinggi Sangat Panjang",
                        groupName = "IMAGE",
                        outputFile = sampleFile,
                        mimeType = "text/plain",
                        summary = "Berhasil diproses"
                    )
                }

                setTestContent(activity, isEnglish = false) {
                    HistoryScreen(container = container)
                }

                val nodes = waitUntilNodes(activity) { current ->
                    findByTag(current, "clear_history_button") != null &&
                        current.any { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("delete_history_") == true }
                }

                val density = activity.resources.displayMetrics.density
                val viewportWidthPx = activity.resources.displayMetrics.widthPixels.toFloat()

                val clearBtn = findByTag(nodes, "clear_history_button")
                assertNotNull(clearBtn)
                val deleteBtn = nodes.firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("delete_history_") == true
                } ?: scrollUntilTag(activity, "delete_history_1")
                val visibleTags = nodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
                val nodeSummary = nodes.mapNotNull { n ->
                    val tag = n.config.getOrNull(SemanticsProperties.TestTag)
                    val txt = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                    if (tag != null || !txt.isNullOrBlank()) "$tag:$txt@${n.boundsInRoot}" else null
                }
                assertNotNull(
                    "Expected delete history icon button; visible tags=$visibleTags; summary=$nodeSummary",
                    deleteBtn
                )
                val deleteBounds = deleteBtn!!.boundsInRoot
                assertTrue(
                    "Delete button width (${deleteBounds.width / density}dp) must meet >= 48dp",
                    deleteBounds.width / density >= 47.5f
                )
                assertTrue(
                    "Delete button height (${deleteBounds.height / density}dp) must meet >= 48dp",
                    deleteBounds.height / density >= 47.5f
                )
                assertTrue(
                    "Delete button right edge (${deleteBounds.right}) must fit inside viewport ($viewportWidthPx)",
                    deleteBounds.right <= viewportWidthPx + 1f
                )
            } finally {
                runBlocking { container.workspace.clearHistory() }
                sampleFile.delete()
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenAndPerspectiveCardHaveAccessibleLabelsAndTargets() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-06",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            val toolNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(toolNodes, "pick_input_button"))
            assertNotNull(findByTag(toolNodes, "run_tool_button"))

            val dummyUri = Uri.fromFile(File(activity.cacheDir, "nonexistent_preview.png"))
            setTestContent(activity, isEnglish = true) {
                PerspectivePointsCard(
                    imageUri = dummyUri,
                    points = listOf(10f, 10f, 90f, 10f, 90f, 90f, 10f, 90f),
                    onPointChange = { _, _ -> }
                )
            }
            val perspectiveNodes = collectSemanticsNodes(activity.window.decorView)
            val expectedDesc = activity.resources.getString(R.string.cd_perspective_preview)
            assertTrue(
                "Perspective preview should expose accessibility contentDescription",
                perspectiveNodes.any { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.contains(expectedDesc) == true
                }
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun audioStudioCompactShowsTimelineFirstAndTogglesMixerFullWidth() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                AudioStudioScreen(container = container, isEnglish = true, onBack = {})
            }
            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(initialNodes, "audio_studio_timeline_pane"))
            assertNull(findByTag(initialNodes, "audio_studio_mixer_panel"))

            val mixerToggle = findByTag(initialNodes, "audio_studio_mixer_toggle")
            assertNotNull(mixerToggle)
            clickNode(mixerToggle!!)
            flushMainUi()

            val toggledNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(toggledNodes, "audio_studio_mixer_panel"))
            assertNull(findByTag(toggledNodes, "audio_studio_timeline_pane"))
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun audioStudioExpandedDisplaysMixerAndTimelineSideBySide() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                AudioStudioScreen(container = container, isEnglish = true, onBack = {})
            }
            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            clickNode(findByTag(initialNodes, "audio_studio_mixer_toggle")!!)
            flushMainUi()

            val wideNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(wideNodes, "audio_studio_mixer_panel"))
            assertNotNull(findByTag(wideNodes, "audio_studio_timeline_pane"))
        }
    }

    @Test
    fun themeProvidesDesignTokensAcrossLightDarkAndAmoledModes() {
        withMainActivity { activity ->
            var amoledBg = HariMuColors.CrispWhite
            var mdSpacingDp = 0f
            var catalogWidthDp = 0f

            setTestContent(activity, isEnglish = true, themeMode = ThemeMode.AMOLED) {
                amoledBg = MaterialTheme.colorScheme.background
                mdSpacingDp = HariMuThemeTokens.spacing.md.value
                catalogWidthDp = HariMuThemeTokens.layout.catalogMaxWidth.value
                Box(Modifier.fillMaxSize())
            }
            assertEquals(HariMuColors.PureBlack, amoledBg)
            assertEquals(16f, mdSpacingDp, 0.01f)
            assertEquals(760f, catalogWidthDp, 0.01f)

            var settingsLicensesTriggered = false
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true, themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    container = container,
                    settings = AppSettings(isEnglish = true, themeMode = ThemeMode.LIGHT.name),
                    onOpenStudio = {},
                    onOpenInternalTool = {},
                    onOpenLicenses = { settingsLicensesTriggered = true }
                )
            }
            val licensesBtn = scrollUntilTag(activity, "open_source_licenses_button")
            assertNotNull(licensesBtn)
            clickNode(licensesBtn!!)
            flushMainUi()
            assertTrue(settingsLicensesTriggered)

            var licenseBackTriggered = false
            setTestContent(activity, isEnglish = true, themeMode = ThemeMode.DARK) {
                OpenSourceLicensesScreen(isEnglish = true, onBack = { licenseBackTriggered = true })
            }
            val licenseNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(licenseNodes, "open_source_licenses_list"))
            clickNode(findByTag(licenseNodes, "licenses_back_button")!!)
            flushMainUi()
            assertTrue(licenseBackTriggered)
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h360dp")
    fun shortWindowUsesNavigationRailInsteadOfBottomBar() {
        withMainActivity { activity ->
            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(nodes, "shell_navigation_rail"))
            assertNull(findByTag(nodes, "shell_bottom_navigation"))
            assertNotNull(findByTag(nodes, "nav_catalog"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun catalogTabOpensCatalogAndReselectingItDoesNotDuplicateBackStack() {
        withMainActivity { activity ->
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "nav_catalog")!!)
            flushMainUi(activity, frames = 12)
            val catalogNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_search_input") != null
            }
            assertNotNull(findByTag(catalogNodes, "catalog_search_input"))
            // The catalog tab is a root destination, so it must not offer an in-app back arrow.
            assertNull(findByTag(catalogNodes, "catalog_back_button"))

            // Re-selecting the active tab must not push a second copy onto the back stack.
            repeat(2) {
                clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "nav_catalog")!!)
                flushMainUi(activity, frames = 8)
            }
            activity.onBackPressedDispatcher.onBackPressed()
            flushMainUi(activity, frames = 12)
            val homeNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_search_input") != null
            }
            assertNotNull(findByTag(homeNodes, "home_search_input"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun groupEntryKeepsRouteParameterAndBackReturnsHome() {
        withMainActivity { activity ->
            val groupCard = scrollUntilTag(activity, "group_card_IMAGE")
            assertNotNull("Expected group_card_IMAGE on Home", groupCard)
            clickNode(groupCard!!)
            flushMainUi(activity, frames = 12)
            val catalogNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_search_input") != null
            }
            assertNotNull(findByTag(catalogNodes, "catalog_search_input"))
            // Deep entries into a group keep an explicit back affordance.
            val backNode = findByTag(
                collectSemanticsNodes(activity.window.decorView, mergingEnabled = true),
                "catalog_back_button"
            )
            assertNotNull(backNode)
            clickNode(backNode!!)
            flushMainUi(activity, frames = 12)
            val restoredNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "catalog_search_input") == null &&
                    (findByTag(nodes, "home_search_input") != null || findByTag(nodes, "group_card_IMAGE") != null)
            }
            assertNull("Expected CatalogScreen to pop back to Home", findByTag(restoredNodes, "catalog_search_input"))
            if (findByTag(restoredNodes, "home_search_input") == null) {
                val scrollable = restoredNodes.lastOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                        node.config.getOrNull(SemanticsActions.ScrollBy) != null
                } ?: restoredNodes.lastOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                scrollable?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, -900f)
                flushMainUi(activity, frames = 4)
            }
            val homeNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "home_search_input") != null
            }
            assertNotNull(findByTag(homeNodes, "home_search_input"))
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

    private fun scrollUntilTag(activity: MainActivity, tag: String): SemanticsNode? {
        repeat(25) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            findByTag(nodes, tag)?.let { return it }
            val scrollable = nodes.lastOrNull { node ->
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                    node.config.getOrNull(SemanticsActions.ScrollBy) != null
            } ?: nodes.lastOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                ?: return null
            scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 900f)
            flushMainUi(activity, frames = 2)
        }
        return findByTag(collectSemanticsNodes(activity.window.decorView), tag)
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

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }
}
