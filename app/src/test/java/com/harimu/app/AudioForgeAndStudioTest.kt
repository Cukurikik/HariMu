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
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.audio.AudioClipInspector
import com.harimu.app.core.design.audio.AudioTimelineMath
import com.harimu.app.core.design.audio.WaveformOverlaySpec
import com.harimu.app.core.design.audio.resolveWaveformOverlaySpec
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.studios.AudioForgeCategory
import com.harimu.app.feature.studios.AudioForgeScreen
import com.harimu.app.feature.studios.AudioStudioScreen
import com.harimu.app.feature.studios.AudioStudioViewModel
import com.harimu.app.feature.studios.EqualizerScreen
import com.harimu.app.feature.studios.categorizeAudioTool
import com.harimu.app.feature.toolscreen.ToolScreen
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class AudioForgeAndStudioTest {

    @Test
    fun audioForgeHasAll28ToolsCategorizedCleanly() {
        val audioTools = ToolRegistry.byGroup(ToolGroup.AUDIO)
        assertEquals(28, audioTools.size)

        val expectedIds = (1..28).map { "AUD-${"%02d".format(it)}" }.toSet()
        assertEquals(expectedIds, audioTools.map { it.id }.toSet())

        val byCategory = AudioForgeCategory.entries.associateWith { cat ->
            audioTools.filter { categorizeAudioTool(it.id) == cat }
        }
        assertEquals(28, byCategory.values.sumOf { it.size })
        AudioForgeCategory.entries.forEach { cat ->
            assertTrue("Category $cat should not be empty", byCategory[cat].orEmpty().isNotEmpty())
        }

        assertEquals(DedicatedScreenRoute.EQUALIZER, ToolRegistry.findById("AUD-02")?.dedicatedRoute)
        assertEquals(DedicatedScreenRoute.AUDIO_STUDIO, ToolRegistry.findById("AUD-21")?.dedicatedRoute)
    }

    @Test
    fun audioTimelineMathComputesOffsetsPeaksGainsAndSummary() {
        val zoom = 2.0f
        val dp = AudioTimelineMath.msToOffsetDp(5_000L, zoom)
        assertEquals(480f, dp, 0.01f)
        assertEquals(5_000L, AudioTimelineMath.offsetDpToMs(dp, zoom, 60_000L))

        val clip = AudioClipSpec(
            id = "c1",
            uriString = "file:///tmp/demo.wav",
            displayName = "demo.wav",
            durationMs = 10_000L,
            trimStartMs = 2_000L,
            trimEndMs = 8_000L,
            offsetMs = 1_500L,
            gainDb = 6.0f
        )
        assertEquals(6_000L, AudioTimelineMath.effectiveClipDurationMs(clip))

        val sliced = AudioTimelineMath.slicePeaksForClip(
            fullPeaks = List(80) { 0.3f },
            clip = clip,
            targetBars = 24
        )
        assertEquals(24, sliced.size)
        assertTrue(sliced.all { it in 0.08f..1f })

        val trackNormal = AudioTrackSpec("t1", "Vocal", volume = 1.0f, pan = 0f, mute = false, solo = false, clips = listOf(clip))
        val trackMuted = AudioTrackSpec("t2", "Beat", volume = 1.0f, pan = -0.5f, mute = true, solo = false, clips = listOf(clip.copy(id = "c2")))
        val trackSolo = AudioTrackSpec("t3", "Synth", volume = 0.8f, pan = 0.5f, mute = false, solo = true, clips = listOf(clip.copy(id = "c3")))

        assertEquals(0f, AudioTimelineMath.computeClipEffectivePlaybackGain(trackMuted, clip, 1.0f, hasAnySolo = false), 0.001f)
        assertEquals(0f, AudioTimelineMath.computeClipEffectivePlaybackGain(trackNormal, clip, 1.0f, hasAnySolo = true), 0.001f)
        assertTrue(AudioTimelineMath.computeClipEffectivePlaybackGain(trackSolo, clip, 1.0f, hasAnySolo = true) > 0f)

        val summary = AudioTimelineMath.activeClipsSummary(listOf(trackNormal, trackMuted, trackSolo))
        assertEquals(1, summary.activeTrackCount)
        assertEquals(1, summary.activeClipCount)
        assertTrue(summary.soloActive)
        assertTrue(summary.hasPanApplied)
        assertEquals(7_500L, summary.totalDurationMs)

        assertTrue(AudioTimelineMath.estimateAudioExportSizeBytes(10_000L, "wav") > AudioTimelineMath.estimateAudioExportSizeBytes(10_000L, "mp3"))
        assertEquals("Center", AudioTimelineMath.formatPan(0.01f, "Center"))
        assertEquals("L 50%", AudioTimelineMath.formatPan(-0.5f, "Center"))
        assertEquals("R 75%", AudioTimelineMath.formatPan(0.75f, "Center"))
    }

    @Test
    fun resolveWaveformOverlaySpecMatchesAudioForgeParams() {
        val fade = resolveWaveformOverlaySpec("AUD-26", mapOf("fadeIn" to 1.5f, "fadeOut" to 2.5f))
        assertTrue(fade is WaveformOverlaySpec.FadeEnvelope)
        assertEquals(1.5f, (fade as WaveformOverlaySpec.FadeEnvelope).fadeInSec, 0.01f)

        val silence = resolveWaveformOverlaySpec("AUD-16", mapOf("thresholdDb" to -35f, "minDurationSec" to 0.8f))
        assertTrue(silence is WaveformOverlaySpec.SilenceThreshold)

        val split = resolveWaveformOverlaySpec("AUD-23", mapOf("mode" to "Jumlah Bagian", "partsCount" to 4))
        assertTrue(split is WaveformOverlaySpec.SplitMarkers)
        assertEquals(3, (split as WaveformOverlaySpec.SplitMarkers).splitFractions.size)

        val noise = resolveWaveformOverlaySpec("AUD-03", mapOf("strength" to 70f, "noiseFloorDb" to -50f))
        assertTrue(noise is WaveformOverlaySpec.NoiseFloor)

        assertEquals(WaveformOverlaySpec.None, resolveWaveformOverlaySpec("AUD-08", emptyMap()))
    }

    @Test
    fun audioStudioViewModelEnforcesTrackClipBoundsAndProjectControls() {
        val app = ApplicationProvider.getApplicationContext<HariMuApp>()
        val vm = AudioStudioViewModel(app.container)

        assertEquals(4, vm.state.value.tracks.size)
        repeat(6) { idx -> vm.addTrack("Extra $idx") }
        assertEquals(8, vm.state.value.tracks.size)

        val firstTrackId = vm.state.value.tracks.first().trackId
        vm.renameTrack(firstTrackId, "Lead Vocal")
        assertEquals("Lead Vocal", vm.state.value.tracks.first().name)

        repeat(10) { vm.deleteTrack(vm.state.value.tracks.last().trackId) }
        assertEquals(1, vm.state.value.tracks.size)

        val remainingTrack = vm.state.value.tracks.first()
        vm.updateTrackSettings(remainingTrack.trackId, volume = 3.5f, pan = -2.0f, mute = true, solo = true)
        val updatedTrack = vm.state.value.tracks.first()
        assertEquals(2.0f, updatedTrack.volume, 0.001f)
        assertEquals(-1.0f, updatedTrack.pan, 0.001f)
        assertTrue(updatedTrack.mute)
        assertTrue(updatedTrack.solo)

        vm.setTitle("Podcast Mix")
        assertEquals("Podcast Mix", vm.state.value.title)
        assertNotNull(vm.state.value.lastSavedAt)

        vm.cancelExport()
        assertNull(vm.state.value.progress)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun audioForgeScreenRendersAllToolsAndFiltersViaSearch() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            var studioOpened = false
            var eqOpened = false
            var openedToolId: String? = null

            setTestContent(activity, isEnglish = true) {
                AudioForgeScreen(
                    container = container,
                    isEnglish = true,
                    onOpenTool = { openedToolId = it.id },
                    onOpenAudioStudio = { studioOpened = true },
                    onOpenEqualizer = { eqOpened = true },
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(nodes, "audio_forge_screen"))
            assertNotNull(findByTag(nodes, "audio_forge_open_studio_btn"))
            assertNotNull(findByTag(nodes, "audio_forge_open_eq_btn"))
            assertNotNull(findByTag(nodes, "audio_forge_search_input"))

            clickNode(findByTag(nodes, "audio_forge_open_studio_btn")!!)
            flushMainUi(activity)
            assertTrue(studioOpened)

            clickNode(findByTag(nodes, "audio_forge_open_eq_btn")!!)
            flushMainUi(activity)
            assertTrue(eqOpened)

            val searchNode = findByTag(nodes, "audio_forge_search_input")!!
            searchNode.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(
                androidx.compose.ui.text.AnnotatedString("8D")
            )
            flushMainUi(activity, frames = 8)

            val filteredNodes = collectSemanticsNodes(activity.window.decorView)
            val card8d = findByTag(filteredNodes, "audio_tool_card_AUD-05")
            assertNotNull("Expected AUD-05 card when searching '8D'", card8d)
            clickNode(card8d!!)
            flushMainUi(activity)
            assertEquals("AUD-05", openedToolId)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun audioStudioScreenRendersRulerExportDialogAndInspector() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                AudioStudioScreen(container = container, isEnglish = true, onBack = {})
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(nodes, "audio_studio_time_ruler"))
            assertNotNull(findByTag(nodes, "audio_studio_empty_card"))
            assertNotNull(findByTag(nodes, "audio_clip_inspector_empty"))
            assertNotNull(findByTag(nodes, "audio_studio_export_button"))

            clickNode(findByTag(nodes, "audio_studio_export_button")!!)
            flushMainUi(activity, frames = 8)

            val dialogNodes = collectAllWindowSemanticsNodes(activity)
            val dialogTags = dialogNodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            val shownCount = org.robolectric.shadows.ShadowDialog.getShownDialogs().size
            val hasLatest = org.robolectric.shadows.ShadowDialog.getLatestDialog() != null
            assertNotNull(
                "Expected audio_export_fmt_wav in dialog (shown=$shownCount, hasLatest=$hasLatest, tags=$dialogTags)",
                findByTag(dialogNodes, "audio_export_fmt_wav")
            )
            assertNotNull(findByTag(dialogNodes, "audio_export_fmt_mp3"))
            assertNotNull(findByTag(dialogNodes, "audio_export_fmt_flac"))

            // Also verify populated AudioClipInspector renders trim/offset/gain & handoffs
            val sampleTrack = AudioTrackSpec("tr_1", "Vocal", 1f, 0f, false, false, emptyList())
            val sampleClip = AudioClipSpec("clip_1", "file:///tmp/vocal.wav", "vocal.wav", 12_000L, 1_000L, 9_000L, 500L, 2.5f)
            var toolHandoff: String? = null
            setTestContent(activity, isEnglish = true) {
                AudioClipInspector(
                    track = sampleTrack,
                    clip = sampleClip,
                    isEnglish = true,
                    onUpdateTrim = { _, _, _, _ -> },
                    onUpdateOffset = { _, _, _ -> },
                    onUpdateGain = { _, _, _ -> },
                    onDeleteClip = { _, _ -> },
                    onOpenInTool = { id, _, _ -> toolHandoff = id }
                )
            }
            val inspectorNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(inspectorNodes, "audio_clip_inspector"))
            assertNotNull(findByTag(inspectorNodes, "audio_inspector_trim_slider"))
            assertNotNull(findByTag(inspectorNodes, "audio_inspector_offset_slider"))
            assertNotNull(findByTag(inspectorNodes, "audio_inspector_gain_slider"))
            assertNotNull(findByTag(inspectorNodes, "audio_inspector_delete_btn"))

            val mergedInspectorNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val masteringChip = mergedInspectorNodes.firstOrNull { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("AUD-01") } == true
            }
            assertNotNull(masteringChip)
            clickNode(masteringChip!!)
            flushMainUi(activity)
            assertEquals("AUD-01", toolHandoff)
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun audioForgeVisualCardsAndEqualizerRenderInWideLayout() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "AUD-01",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            assertNotNull(scrollUntilTag(activity, "audio_visual_mastering_card"))

            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "AUD-05",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            assertNotNull(scrollUntilTag(activity, "audio_visual_spatial_8d_card"))

            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "AUD-28",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            assertNotNull(scrollUntilTag(activity, "audio_visual_visualizer_card"))

            setTestContent(activity, isEnglish = true) {
                EqualizerScreen(container = container, isEnglish = true, onBack = {})
            }
            val eqNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(eqNodes, "eq_pick_audio_button"))
            assertNotNull(findByTag(eqNodes, "eq_parity_card"))
            assertNotNull(findByTag(eqNodes, "eq_reset_button"))
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
        fun layoutDecor(decor: View, widthPx: Int, heightPx: Int) {
            forceLayoutTree(decor)
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, widthPx, heightPx)
            decor.viewTreeObserver.dispatchOnPreDraw()
        }
        repeat(frames) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            activity?.let { act ->
                val dm = act.resources.displayMetrics
                act.window?.decorView?.let { decor ->
                    layoutDecor(decor, dm.widthPixels, dm.heightPixels)
                }
                org.robolectric.shadows.ShadowDialog.getShownDialogs().forEach { dialog ->
                    dialog.window?.decorView?.let { dialogDecor ->
                        layoutDecor(dialogDecor, dm.widthPixels, dm.heightPixels)
                    }
                }
            }
        }
    }

    private fun scrollUntilTag(activity: MainActivity, tag: String): SemanticsNode? {
        repeat(20) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            findByTag(nodes, tag)?.let { return it }
            val scrollable = nodes.lastOrNull { node ->
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                    node.config.getOrNull(SemanticsActions.ScrollBy) != null
            } ?: nodes.lastOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                ?: return null
            scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 800f)
            flushMainUi(activity, frames = 2)
        }
        return findByTag(collectSemanticsNodes(activity.window.decorView), tag)
    }

    private fun collectAllWindowSemanticsNodes(
        activity: MainActivity,
        mergingEnabled: Boolean = false
    ): List<SemanticsNode> {
        val dm = activity.resources.displayMetrics
        val globalViews = runCatching {
            android.view.inspector.WindowInspector.getGlobalWindowViews()
        }.getOrNull().orEmpty()
        val shownDialogs = org.robolectric.shadows.ShadowDialog.getShownDialogs() +
            listOfNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog())
        val dialogViews = shownDialogs.mapNotNull { dialog ->
            dialog.window?.decorView
        }
        val windowRoots = (dialogViews + globalViews + listOfNotNull(activity.window?.decorView)).distinct()
        windowRoots.forEach { decor ->
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
            decor.viewTreeObserver.dispatchOnPreDraw()
        }
        return windowRoots.flatMap { view -> collectSemanticsNodes(view, mergingEnabled) }
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
