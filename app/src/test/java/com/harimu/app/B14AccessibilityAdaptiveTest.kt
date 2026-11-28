package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.HariMuTopBar
import com.harimu.app.core.design.ProgressCallbacks
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.ToolProgressState
import com.harimu.app.core.design.ToolProgressView
import com.harimu.app.navigation.HariMuAppShell
import com.harimu.app.navigation.SHELL_BOTTOM_NAVIGATION_TAG
import com.harimu.app.navigation.SHELL_NAVIGATION_RAIL_TAG
import org.junit.Assert.assertEquals
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

/**
 * B14 accessibility, adaptive-layout and lifecycle verification.
 *
 * These tests pin the cross-cutting behaviour rather than per-screen copy: async status banners and
 * job outcomes must be announced (live regions), screen titles must be heading navigation targets,
 * the shell must pick bottom navigation or a rail from the window size class, and narrow phones
 * must still render the real app shell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class B14AccessibilityAdaptiveTest {

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun statusBanner_errorIsAssertiveLiveRegion() {
        withMainActivity { activity ->
            setTestContent(activity, isEnglish = true) {
                HariMuStatusBanner(
                    message = "Failed to load the weather forecast",
                    tone = StatusTone.ERROR
                )
            }

            val nodes = waitUntilNodes(activity) { current -> liveRegionNode(current) != null }
            val banner = liveRegionNode(nodes)
            assertNotNull("Expected a live region on the error banner", banner)
            assertEquals(LiveRegionMode.Assertive, banner!!.config.getOrNull(SemanticsProperties.LiveRegion))
            assertTrue(
                "Expected the banner message to stay readable",
                collectAllText(nodes).any { it.contains("Failed to load the weather forecast") }
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun statusBanner_warningIsPoliteLiveRegion() {
        withMainActivity { activity ->
            setTestContent(activity, isEnglish = true) {
                HariMuStatusBanner(
                    message = "Showing the last saved forecast",
                    tone = StatusTone.WARNING
                )
            }

            val nodes = waitUntilNodes(activity) { current -> liveRegionNode(current) != null }
            val banner = liveRegionNode(nodes)
            assertNotNull("Expected a live region on the warning banner", banner)
            assertEquals(LiveRegionMode.Polite, banner!!.config.getOrNull(SemanticsProperties.LiveRegion))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun failedJobCard_announcesOutcomeAndKeepsRetryTarget() {
        withMainActivity { activity ->
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = true) {
                ToolProgressView(
                    state = ToolProgressState.Failed(error = "Encoder stopped", isRetryable = true),
                    callbacks = ProgressCallbacks(onRetry = {})
                )
            }

            val nodes = waitUntilNodes(activity) { current -> liveRegionNode(current) != null }
            val card = liveRegionNode(nodes)
            assertNotNull("Expected a live region on the failed job card", card)
            assertEquals(LiveRegionMode.Polite, card!!.config.getOrNull(SemanticsProperties.LiveRegion))
            assertTrue(
                "Expected the failure reason to be announced",
                collectAllText(nodes).any { it.contains("Encoder stopped") }
            )
            val clickable = nodes.filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue("Expected a recovery action on the failed job card", clickable.isNotEmpty())
            clickable.forEach { node -> assertMinTouchTarget(node, density, checkWidth = false) }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun topBarTitle_isHeadingTarget() {
        withMainActivity { activity ->
            setTestContent(activity, isEnglish = true) {
                HariMuTopBar(title = "Beranda", onBack = {})
            }

            val nodes = waitUntilNodes(activity) { current ->
                current.any { it.config.getOrNull(SemanticsProperties.Heading) != null }
            }
            val heading = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Heading) != null }
            assertNotNull("Expected the app bar title to expose heading semantics", heading)
            assertEquals(
                "Beranda",
                heading!!.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            )
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun compactPhone_rendersRealShellWithBottomNavigation() {
        withMainActivity { activity ->
            val nodes = waitUntilNodes(activity) { current ->
                findByTag(current, SHELL_BOTTOM_NAVIGATION_TAG) != null &&
                    findByTag(current, "home_search_input") != null
            }
            assertNotNull(
                "Expected bottom navigation on a 320dp-wide phone",
                findByTag(nodes, SHELL_BOTTOM_NAVIGATION_TAG)
            )
            assertNull(
                "Expected no navigation rail on a 320dp-wide phone",
                findByTag(nodes, SHELL_NAVIGATION_RAIL_TAG)
            )
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun shell_compactWidth_usesBottomNavigation() {
        withMainActivity { activity ->
            setTestContent(activity, isEnglish = true) {
                HariMuAppShell(
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    heightSizeClass = WindowHeightSizeClass.Medium,
                    currentDestination = null,
                    showNavigation = true,
                    onSelectDestination = {}
                ) { padding ->
                    Column(modifier = Modifier.padding(padding)) {}
                }
            }

            val nodes = waitUntilNodes(activity) { current ->
                findByTag(current, SHELL_BOTTOM_NAVIGATION_TAG) != null
            }
            assertNotNull(
                "Expected bottom navigation for the compact width class",
                findByTag(nodes, SHELL_BOTTOM_NAVIGATION_TAG)
            )
            assertNull(
                "Expected no rail for the compact width class",
                findByTag(nodes, SHELL_NAVIGATION_RAIL_TAG)
            )
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun shell_tabletWidth_usesNavigationRail() {
        withMainActivity { activity ->
            setTestContent(activity, isEnglish = true) {
                HariMuAppShell(
                    widthSizeClass = WindowWidthSizeClass.Expanded,
                    heightSizeClass = WindowHeightSizeClass.Expanded,
                    currentDestination = null,
                    showNavigation = true,
                    onSelectDestination = {}
                ) { padding ->
                    Column(modifier = Modifier.padding(padding)) {}
                }
            }

            val nodes = waitUntilNodes(activity) { current ->
                findByTag(current, SHELL_NAVIGATION_RAIL_TAG) != null
            }
            assertNotNull(
                "Expected a navigation rail on a tablet-width window",
                findByTag(nodes, SHELL_NAVIGATION_RAIL_TAG)
            )
            assertNull(
                "Expected no bottom navigation on a tablet-width window",
                findByTag(nodes, SHELL_BOTTOM_NAVIGATION_TAG)
            )
        }
    }

    private fun liveRegionNode(nodes: List<SemanticsNode>): SemanticsNode? =
        nodes.firstOrNull { node -> node.config.getOrNull(SemanticsProperties.LiveRegion) != null }

    private fun assertMinTouchTarget(
        node: SemanticsNode,
        density: Float,
        minDp: Float = 48f,
        checkWidth: Boolean = true
    ) {
        val bounds = node.boundsInRoot
        val heightDp = (bounds.bottom - bounds.top) / density
        val widthDp = (bounds.right - bounds.left) / density
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        assertTrue("Touch target height ${heightDp}dp < ${minDp}dp for $tag", heightDp >= minDp - 0.5f)
        if (checkWidth) {
            assertTrue("Touch target width ${widthDp}dp < ${minDp}dp for $tag", widthDp >= minDp - 0.5f)
        }
    }

    private fun collectAllText(nodes: List<SemanticsNode>): List<String> =
        nodes.flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
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
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val baseDensity = LocalDensity.current
            val targetLocale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("id")
            val localizedConfig = remember(baseConfig, targetLocale, fontScale) {
                Configuration(baseConfig).apply {
                    setLocale(targetLocale)
                    this.fontScale = fontScale
                }
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
                LocalDensity provides Density(baseDensity.density, fontScale),
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

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }
}
