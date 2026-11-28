package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.feature.studios.VoiceRecorderScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * B15 cross-screen integration: every screen chrome is served by one component so title
 * typography, colours, heading semantics and the back/refresh affordances cannot drift apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class B15CrossScreenConsistencyTest {

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun utilityScaffold_rendersSharedTopBarChrome() {
        withMainActivity { activity ->
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = true) {
                UtilityScreenScaffold(
                    title = "BMKG Weather",
                    subtitle = "Jakarta",
                    onBack = {},
                    onRefresh = {}
                ) { _ ->
                    Text("content")
                }
            }

            val nodes = waitUntilNodes(activity) { current -> headingNode(current) != null }
            val heading = headingNode(nodes)
            assertNotNull("Expected the utility top bar title to be a heading", heading)
            assertEquals(
                "BMKG Weather",
                heading!!.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            )

            val back = findByTag(nodes, "utility_back_button")
            assertNotNull("Expected the shared back button tag to survive the migration", back)
            assertFalse(
                "Expected a content description on the shared back button",
                back!!.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
            )
            assertMinTouchTarget(back, density)

            val refresh = findByTag(nodes, "utility_refresh_button")
            assertNotNull("Expected the shared refresh button tag", refresh)
            assertMinTouchTarget(refresh!!, density)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun studioScreen_rendersSharedTopBarChrome() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = false) {
                VoiceRecorderScreen(
                    container = container,
                    isEnglish = false,
                    onBack = {}
                )
            }

            val nodes = waitUntilNodes(activity) { current -> headingNode(current) != null }
            assertNotNull("Expected the studio top bar title to be a heading", headingNode(nodes))
            val back = findByTag(nodes, "voice_recorder_back_button")
            assertNotNull("Expected voice_recorder_back_button", back)
            assertFalse(
                "Expected a content description on the studio back button",
                back!!.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
            )
        }
    }

    private fun headingNode(nodes: List<SemanticsNode>): SemanticsNode? =
        nodes.firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Heading) != null }

    private fun assertMinTouchTarget(
        node: SemanticsNode,
        density: Float,
        minDp: Float = 48f
    ) {
        val bounds = node.boundsInRoot
        val heightDp = (bounds.bottom - bounds.top) / density
        val widthDp = (bounds.right - bounds.left) / density
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        assertTrue("Touch target height ${heightDp}dp < ${minDp}dp for $tag", heightDp >= minDp - 0.5f)
        assertTrue("Touch target width ${widthDp}dp < ${minDp}dp for $tag", widthDp >= minDp - 0.5f)
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
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val baseDensity = LocalDensity.current
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
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale),
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity
            ) {
                HariMuTheme(
                    themeMode = ThemeMode.SYSTEM,
                    isSystemDark = false,
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
        nodes.firstOrNull { node -> node.config.getOrNull(SemanticsProperties.TestTag) == tag }
}
