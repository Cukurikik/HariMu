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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.video.ToolSearchBar
import com.harimu.app.core.recording.RecordingUiState
import com.harimu.app.core.recording.ScreenRecordingController
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.history.JobResultDetailSheet
import com.harimu.app.feature.settings.SettingsScreen
import com.harimu.app.feature.studios.KeyVaultScreen
import com.harimu.app.feature.studios.ScreenRecorderScreen
import com.harimu.app.feature.studios.VideoEditorScreen
import com.harimu.app.feature.studios.VideoForgeScreen
import com.harimu.app.feature.studios.VoiceRecorderScreen
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class P13UxAndAccessibilityTest {

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun voiceRecorderScreen_compactAndLargeFont_scrollsAndExposes48dpTargets() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = true, fontScale = 1.5f) {
                VoiceRecorderScreen(
                    container = container,
                    isEnglish = true,
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val backNode = findByTag(nodes, "voice_recorder_back_button")
            assertNotNull("Expected voice_recorder_back_button", backNode)
            assertFalse(
                "Expected non-blank contentDescription on voice_recorder_back_button",
                backNode!!.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().isNullOrBlank()
            )
            assertMinTouchTarget(backNode, density)

            val grantBtn = scrollUntilTag(activity, "voice_grant_mic_button")
            assertNotNull("Expected voice_grant_mic_button reachable via scroll", grantBtn)
            assertMinTouchTarget(grantBtn!!, density, checkWidth = false)
            assertTrue(
                "Expected English permission CTA on VoiceRecorderScreen",
                collectAllText(collectSemanticsNodes(activity.window.decorView)).any {
                    it.contains("Grant Microphone Permission", ignoreCase = true)
                }
            )
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp")
    fun voiceRecorderScreen_landscape_scrollsToGrantMicButton() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = false) {
                VoiceRecorderScreen(
                    container = container,
                    isEnglish = false,
                    onBack = {}
                )
            }

            val grantBtn = scrollUntilTag(activity, "voice_grant_mic_button")
            assertNotNull("Expected voice_grant_mic_button reachable in landscape", grantBtn)
            assertMinTouchTarget(grantBtn!!, density, checkWidth = false)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun screenRecorderScreen_englishLabelsSwitchSemanticsAndPauseCd() {
        try {
            ScreenRecordingController.update(RecordingUiState(running = false, paused = false))
            withMainActivity { activity ->
                val container = (activity.application as HariMuApp).container
                val density = activity.resources.displayMetrics.density
                setTestContent(activity, isEnglish = true, fontScale = 1.5f) {
                    ScreenRecorderScreen(
                        container = container,
                        isEnglish = true,
                        onBack = {}
                    )
                }

                val mergedNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
                val backNode = findByTag(mergedNodes, "screen_recorder_back_button")
                assertNotNull("Expected screen_recorder_back_button", backNode)
                assertFalse(
                    "Expected non-blank contentDescription on screen_recorder_back_button",
                    backNode!!.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().isNullOrBlank()
                )
                assertMinTouchTarget(backNode, density)

                val allText = collectAllText(collectSemanticsNodes(activity.window.decorView))
                assertTrue("Expected localized 'Microphone' chip in English", allText.any { it == "Microphone" })
                assertFalse("Unexpected hardcoded 'Mikrofon' in English mode", allText.any { it == "Mikrofon" })

                val floatingRow = scrollUntilTag(activity, "screen_floating_controls_row")
                assertNotNull("Expected screen_floating_controls_row", floatingRow)
                assertNotNull(
                    "Expected ToggleableState on screen_floating_controls_row",
                    floatingRow!!.config.getOrNull(SemanticsProperties.ToggleableState)
                )
                assertEquals(
                    Role.Switch,
                    floatingRow.config.getOrNull(SemanticsProperties.Role)
                )
                assertEquals(ToggleableState.Off, floatingRow.config.getOrNull(SemanticsProperties.ToggleableState))

                val recordToggle = scrollUntilTag(activity, "screen_record_toggle")
                assertNotNull("Expected screen_record_toggle", recordToggle)
                assertMinTouchTarget(recordToggle!!, density, checkWidth = false)

                ScreenRecordingController.update(RecordingUiState(running = true, paused = false))
                waitUntilNodes(activity) { nodes ->
                    findByTag(nodes, "screen_record_pause_button") != null
                }
                val pauseButton = scrollUntilTag(activity, "screen_record_pause_button")
                assertNotNull("Expected screen_record_pause_button while running", pauseButton)
                assertMinTouchTarget(pauseButton!!, density)
                val mergedRunning = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
                val pauseMerged = findByTag(mergedRunning, "screen_record_pause_button")!!
                val pauseDesc = pauseMerged.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                assertFalse("Expected non-blank contentDescription on pause button", pauseDesc.isNullOrBlank())
            }
        } finally {
            ScreenRecordingController.update(RecordingUiState())
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun settingsScreen_largeFontAndAsyncStorageExpose48dpTouchTargets() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = true, fontScale = 1.5f) {
                SettingsScreen(
                    container = container,
                    settings = AppSettings(isEnglish = true),
                    onOpenStudio = {},
                    onOpenInternalTool = {},
                    onOpenLicenses = {}
                )
            }

            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            listOf("theme_chip_SYSTEM", "theme_chip_LIGHT", "theme_chip_DARK", "theme_chip_AMOLED").forEach { tag ->
                val chip = findByTag(initialNodes, tag) ?: scrollUntilTag(activity, tag)
                assertNotNull("Expected $tag", chip)
                assertMinTouchTarget(chip!!, density, checkWidth = false)
            }

            val clearTemp = scrollUntilTag(activity, "clear_temp_button")
            assertNotNull("Expected clear_temp_button", clearTemp)
            assertMinTouchTarget(clearTemp!!, density, checkWidth = false)

            val licensesBtn = scrollUntilTag(activity, "open_source_licenses_button")
            assertNotNull("Expected open_source_licenses_button", licensesBtn)
            assertMinTouchTarget(licensesBtn!!, density, checkWidth = false)
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp")
    fun videoEditorScreen_landscapeButtonsMeet48dpMinimumTargets() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = true) {
                VideoEditorScreen(
                    container = container,
                    isEnglish = true,
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            listOf(
                "editor_back_button",
                "editor_projects_button",
                "editor_save_button",
                "editor_add_clips_landscape_button"
            ).forEach { tag ->
                val node = findByTag(nodes, tag) ?: scrollUntilTag(activity, tag)
                assertNotNull("Expected $tag in landscape VideoEditorScreen", node)
                assertMinTouchTarget(node!!, density, checkWidth = tag != "editor_add_clips_landscape_button")
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolSearchBar_clearButtonHasLocalizedCdAnd48dpTarget() {
        withMainActivity { activity ->
            val density = activity.resources.displayMetrics.density
            var cleared = false
            setTestContent(activity, isEnglish = true) {
                ToolSearchBar(
                    query = "trim",
                    onQueryChange = {},
                    onClear = { cleared = true }
                )
            }

            val merged = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val clearNode = merged.firstOrNull { node ->
                val cd = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                cd == "Clear" && node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertNotNull("Expected localized 'Clear' button in ToolSearchBar", clearNode)
            assertMinTouchTarget(clearNode!!, density)
            clickNode(clearNode)
            flushMainUi(activity)
            assertTrue("Expected onClear callback to fire", cleared)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun videoForgeScreen_standaloneFallbackHandlesSystemBack() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                VideoForgeScreen(
                    container = container,
                    isEnglish = true,
                    onOpenTool = null,
                    onBack = {}
                )
            }

            val forgeNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            assertNotNull(findByTag(forgeNodes, "video_forge_back_button"))
            val trimCard = forgeNodes.firstOrNull { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
                        it.text.contains("Trim", ignoreCase = true)
                    }
            }
            assertNotNull("Expected clickable Trim Video card inside VideoForgeScreen", trimCard)
            val upscalerNode = scrollUntilPredicate(activity) { nodes ->
                collectAllText(nodes).any { it.contains("Video Upscaler", ignoreCase = true) }
            }
            assertTrue(
                "Expected VID-07 Video Upscaler to be visible in VideoForgeScreen",
                collectAllText(upscalerNode).any { it.contains("Video Upscaler", ignoreCase = true) }
            )
            clickNode(trimCard!!)

            val toolNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "tool_back_button") != null
            }
            assertNotNull("Expected embedded ToolScreen when VideoForgeScreen uses standalone fallback", findByTag(toolNodes, "tool_back_button"))

            activity.onBackPressedDispatcher.onBackPressed()
            val restoredNodes = waitUntilNodes(activity) { nodes ->
                findByTag(nodes, "video_forge_back_button") != null
            }
            assertNotNull("Expected BackHandler to return to VideoForgeScreen", findByTag(restoredNodes, "video_forge_back_button"))
            assertNull(findByTag(restoredNodes, "tool_back_button"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun catalogScreen_videoGroupExposesAccessibleVideoForgeCta() {
        withMainActivity { activity ->
            val density = activity.resources.displayMetrics.density
            var openedVideoForge = false
            setTestContent(activity, isEnglish = true) {
                CatalogScreen(
                    initialGroup = ToolGroup.VIDEO,
                    isEnglish = true,
                    favoriteIds = emptySet(),
                    onOpenTool = {},
                    onToggleFavorite = {},
                    onOpenVideoForge = { openedVideoForge = true }
                )
            }
            val nodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val cta = findByTag(nodes, "catalog_open_video_forge_btn")
            assertNotNull("Expected catalog_open_video_forge_btn in VIDEO catalog group", cta)
            assertMinTouchTarget(cta!!, density, checkWidth = false)
            clickNode(cta)
            flushMainUi(activity)
            assertTrue("Expected Video Forge CTA callback to invoke", openedVideoForge)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun keyVaultAndJobDetailSheet_renderWithAccessibleTargetsAndSafeIo() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val density = activity.resources.displayMetrics.density
            setTestContent(activity, isEnglish = true) {
                KeyVaultScreen(
                    container = container,
                    isEnglish = true,
                    onBack = {}
                )
            }
            val vaultNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val vaultBack = findByTag(vaultNodes, "key_vault_back_button")
            assertNotNull("Expected key_vault_back_button", vaultBack)
            assertFalse(
                vaultBack!!.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().isNullOrBlank()
            )
            assertMinTouchTarget(vaultBack, density)
            val generateBtn = scrollUntilTag(activity, "generate_key_button")
            assertNotNull("Expected generate_key_button", generateBtn)
            assertMinTouchTarget(generateBtn!!, density, checkWidth = false)

            val missingOutputJob = JobEntity(
                jobId = "job-test-1",
                workRequestId = "work-1",
                toolId = "IMG-01",
                toolTitle = "Compress Image",
                groupName = "IMAGE",
                isDummy = false,
                status = "SUCCEEDED",
                outputPath = "/nonexistent/path/missing.jpg",
                outputMime = "image/jpeg",
                summary = "Done"
            )
            setTestContent(activity, isEnglish = true) {
                JobResultDetailSheet(
                    job = missingOutputJob,
                    history = null,
                    uploadTasks = emptyList(),
                    container = container,
                    isEnglish = true,
                    dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
                    canRetry = false,
                    onCancelJob = {},
                    onRetryJob = {},
                    onRetryUpload = {},
                    onUploadOutput = {},
                    onSaveOutput = {},
                    onOpenOutput = {},
                    onShareOutput = {},
                    onOpenTool = { _, _ -> },
                    onDismiss = {}
                )
            }
            val sheetNodes = waitUntilNodes(activity) { nodes ->
                collectAllText(nodes).any { it.contains("Compress Image") }
            }
            assertTrue(
                "Expected JobResultDetailSheet to render Compress Image safely",
                collectAllText(sheetNodes).any { it.contains("Compress Image") }
            )
        }
    }

    private fun assertMinTouchTarget(
        node: SemanticsNode,
        density: Float,
        minDp: Float = 48f,
        checkWidth: Boolean = true
    ) {
        val bounds = node.boundsInRoot
        val heightDp = (bounds.bottom - bounds.top) / density
        val widthDp = (bounds.right - bounds.left) / density
        assertTrue(
            "Touch target height ${heightDp}dp < ${minDp}dp for ${node.config.getOrNull(SemanticsProperties.TestTag)}",
            heightDp >= minDp - 0.5f
        )
        if (checkWidth) {
            assertTrue(
                "Touch target width ${widthDp}dp < ${minDp}dp for ${node.config.getOrNull(SemanticsProperties.TestTag)}",
                widthDp >= minDp - 0.5f
            )
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

    private fun scrollUntilTag(activity: MainActivity, tag: String): SemanticsNode? {
        val nodes = scrollUntilPredicate(activity) { current ->
            val node = findByTag(current, tag) ?: return@scrollUntilPredicate false
            node.size.height > 0 && (node.boundsInRoot.bottom - node.boundsInRoot.top) >= node.size.height - 1f
        }
        return findByTag(nodes, tag)
    }

    private fun scrollUntilPredicate(
        activity: MainActivity,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        repeat(25) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            val scrollable = nodes.lastOrNull { node ->
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                    node.config.getOrNull(SemanticsActions.ScrollBy) != null
            } ?: nodes.lastOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                ?: return nodes
            scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 520f)
            flushMainUi(activity, frames = 2)
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

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }
}
