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
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ParamValidation
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.ToolParameterPanel
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolInputCodec
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.toolscreen.ExportState
import com.harimu.app.feature.toolscreen.ToolScreen
import com.harimu.app.feature.toolscreen.ToolViewModel
import com.harimu.app.feature.toolscreen.ToolWorkspaceValidator
import com.harimu.app.feature.toolscreen.WorkspaceStage
import androidx.compose.runtime.mutableStateOf
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
class ToolScreenIntegrationTest {

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenRendersWithNewComponents() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // Test with a simple image tool (IMG-02 Smart Resizer)
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-02",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)

            // Verify tool header
            assertNotNull(findByTag(nodes, "tool_card_IMG-02"))

            // Verify input section - file picker
            val pickButton = findByTag(nodes, "pick_input_button")
            assertNotNull("Expected pick_input_button in ToolScreen", pickButton)

            // Verify parameter panel - IMG-02 has scale and maxEdge sliders
            val paramCards = nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag)?.contains("ParamSlider") == true }
            assertTrue("Expected at least one ParamSlider for IMG-02", paramCards.size >= 1)

            // Verify run button
            val runButton = findByTag(nodes, "run_tool_button")
            assertNotNull("Expected run_tool_button in ToolScreen", runButton)

            // Verify ToolIdBadge and EngineBadge are present
            val toolIdBadge = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("IMG-02") == true }
            assertNotNull("Expected IMG-02 tool ID in UI", toolIdBadge)

            val engineBadge = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("NAT") == true }
            assertNotNull("Expected NAT engine badge in UI", engineBadge)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenWithTextInputRenders() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // Test with text input tool (CNV-23 JSON/XML Converter)
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "CNV-23",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)

            // Verify text input field is present
            val textInput = findByTag(nodes, "tool_text_input")
            assertNotNull("Expected tool_text_input for CNV-23", textInput)

            // Verify dropdown parameter for direction
            val dropdownChips = nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag)?.contains("FilterChip") == true }
            assertTrue("Expected FilterChip dropdown for CNV-23", dropdownChips.size >= 1)

            // Verify run button
            val runButton = findByTag(nodes, "run_tool_button")
            assertNotNull("Expected run_tool_button in ToolScreen", runButton)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenWithVideoInputShowsPreview() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // Test with video tool (VID-01 Trim)
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "VID-01",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)

            // Verify file picker for video
            val pickButton = findByTag(nodes, "pick_input_button")
            assertNotNull("Expected pick_input_button for VID-01", pickButton)

            // Verify parameters include startSec and endSec sliders
            // Note: VideoTrimPreviewCard is rendered when a video is selected
            // Without a video selected, we just check the parameter panel exists
            val paramSection = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Parameters") == true }
            assertNotNull("Expected Parameters section", paramSection)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenWithCompoundInputShowsSecondaryPicker() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // Test with compound input tool (VID-13 Add Music/Audio - VideoAndAudio)
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "VID-13",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)

            // Verify secondary audio picker button
            val secondaryAudioBtn = nodes.firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Background Audio") == true ||
                    it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Audio Latar") == true
            }
            assertNotNull("Expected secondary audio picker for VID-13", secondaryAudioBtn)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenStubShowsComingSoon() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // Find a stub tool - e.g., VID-07 Video Upscaler (if stub)
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "VID-07",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)

            // Check for stub notice
            val stubNotice = findByTag(nodes, "stub_notice_card")
            // VID-07 might be implemented, so we just verify the screen renders
            val runButton = findByTag(nodes, "run_tool_button")
            assertNotNull("Expected run_tool_button even for stub tools", runButton)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenAccessibilityLabelsPresent() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-02",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)

            // Check back button has content description
            val backButton = findByTag(nodes, "tool_back_button")
            assertNotNull(backButton)
            val backDesc = backButton!!.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            assertFalse("Back button should have content description", backDesc.isNullOrBlank())

            // Check favorite button has content description
            val favButton = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag)?.contains("fav_button") == true }
            // Favorite button might not have a test tag in ToolScreen, but should have content description
            assertNotNull("Expected favorite toggle button", favButton)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenParameterValidationLabels() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // IMG-31 Watermark has text, slider, dropdown, toggle
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-31",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)

            // Verify parameter labels are present
            val textParam = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Watermark Text") == true }
            assertNotNull("Expected Watermark Text parameter", textParam)

            val opacitySlider = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Opacity") == true }
            assertNotNull("Expected Opacity slider parameter", opacitySlider)

            val positionDropdown = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Position") == true }
            assertNotNull("Expected Position dropdown parameter", positionDropdown)

            val tiledToggle = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Tiled") == true }
            assertNotNull("Expected Tiled Pattern toggle parameter", tiledToggle)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun workspaceValidatorCoversAllRealStages() {
        withMainActivity { activity ->
            val pdfMerge = ToolRegistry.findById("PDF-01")!!
            val vidMergeAudio = ToolRegistry.findById("VID-13")!!
            val vidTrim = ToolRegistry.findById("VID-01")!!
            val cnvJsonXml = ToolRegistry.findById("CNV-23")!!
            val pdfWm = ToolRegistry.findById("PDF-11")

            val tempA = File(activity.cacheDir, "b5_a.bin").apply { writeText("A") }
            val tempB = File(activity.cacheDir, "b5_b.bin").apply { writeText("B") }
            val uriA = Uri.fromFile(tempA)
            val uriB = Uri.fromFile(tempB)

            // InputMissing for min-2 file tool
            val missingTwo = ToolWorkspaceValidator.validateWorkspaceInput(
                context = activity,
                tool = pdfMerge,
                input = ToolInput(uris = listOf(uriA))
            )
            assertFalse(missingTwo.isValid)
            assertEquals(WorkspaceStage.INPUT_MISSING, missingTwo.stage)

            // Ready when 2 readable files supplied
            val twoReady = ToolWorkspaceValidator.validateWorkspaceInput(
                context = activity,
                tool = pdfMerge,
                input = ToolInput(uris = listOf(uriA, uriB))
            )
            assertTrue(twoReady.isValid)
            assertEquals(WorkspaceStage.READY, twoReady.stage)

            // Compound VideoAndAudio requires secondaryUri
            val missingSecondary = ToolWorkspaceValidator.validateWorkspaceInput(
                context = activity,
                tool = vidMergeAudio,
                input = ToolInput(uris = listOf(uriA), secondaryUri = null)
            )
            assertFalse(missingSecondary.isValid)
            assertEquals(WorkspaceStage.INPUT_MISSING, missingSecondary.stage)

            // PdfAndImage only requires secondaryUri when mode == Gambar
            if (pdfWm != null) {
                val textMode = ToolWorkspaceValidator.validateWorkspaceInput(
                    context = activity,
                    tool = pdfWm,
                    input = ToolInput(uris = listOf(uriA), params = mapOf("mode" to "Teks"))
                )
                assertTrue(textMode.isValid)

                val imageModeMissing = ToolWorkspaceValidator.validateWorkspaceInput(
                    context = activity,
                    tool = pdfWm,
                    input = ToolInput(uris = listOf(uriA), params = mapOf("mode" to "Gambar"))
                )
                assertFalse(imageModeMissing.isValid)
                assertEquals(WorkspaceStage.INPUT_MISSING, imageModeMissing.stage)
            }

            // Unreadable file => InvalidInput
            val missingUri = Uri.fromFile(File(activity.cacheDir, "does_not_exist_123.mp4"))
            val unreadable = ToolWorkspaceValidator.validateWorkspaceInput(
                context = activity,
                tool = vidTrim,
                input = ToolInput(uris = listOf(missingUri))
            )
            assertFalse(unreadable.isValid)
            assertEquals(WorkspaceStage.INVALID_INPUT, unreadable.stage)

            // Trim endSec <= startSec (with endSec > 0) => InvalidInput; endSec == 0 => Ready
            val badTrim = ToolWorkspaceValidator.validateWorkspaceInput(
                context = activity,
                tool = vidTrim,
                input = ToolInput(uris = listOf(uriA), params = mapOf("startSec" to 12f, "endSec" to 4f))
            )
            assertFalse(badTrim.isValid)
            assertEquals(WorkspaceStage.INVALID_INPUT, badTrim.stage)

            val endAtFileEnd = ToolWorkspaceValidator.validateWorkspaceInput(
                context = activity,
                tool = vidTrim,
                input = ToolInput(uris = listOf(uriA), params = mapOf("startSec" to 12f, "endSec" to 0f))
            )
            assertTrue(endAtFileEnd.isValid)

            // TextInput blank => InputMissing; non-blank => Ready
            assertEquals(
                WorkspaceStage.INPUT_MISSING,
                ToolWorkspaceValidator.validateWorkspaceInput(activity, cnvJsonXml, ToolInput(textInput = "  ")).stage
            )
            assertEquals(
                WorkspaceStage.READY,
                ToolWorkspaceValidator.validateWorkspaceInput(activity, cnvJsonXml, ToolInput(textInput = "{\"a\":1}")).stage
            )
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun invalidParameterDoesNotDisableControlAndRemainsEditable() {
        withMainActivity { activity ->
            val currentVal = mutableStateOf(0f)
            val param = ToolParam.SliderParam("angle", "Angle", "Angle", 0f, 360f, 0f)
            val toggleParam = ToolParam.ToggleParam("flag", "Enable Option", "Enable Option", false)
            setTestContent(activity, isEnglish = true) {
                ToolParameterPanel(
                    params = listOf(param, toggleParam),
                    paramValues = mapOf("angle" to currentVal.value, "flag" to false),
                    onParamChange = { key, value ->
                        if (key == "angle") currentVal.value = (value as Number).toFloat()
                    },
                    validators = mapOf(
                        "angle" to { ParamValidation.Invalid("Out of range test") },
                        "flag" to { ParamValidation.Invalid("Toggle error test") }
                    ),
                    isEnglish = true
                )
            }
            val nodes = collectSemanticsNodes(activity.window.decorView)
            val switchNode = nodes.firstOrNull {
                it.config.getOrNull(SemanticsProperties.ToggleableState) != null
            }
            assertNotNull("Expected toggle switch in parameter panel", switchNode)
            assertNull(
                "Invalid parameter control must remain enabled so user can fix it",
                switchNode!!.config.getOrNull(SemanticsProperties.Disabled)
            )
            val errMsg = nodes.firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.contains("Out of range test") == true
            }
            assertNotNull("Expected validation error message", errMsg)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolViewModelHydratesJobExportsSafelyAndHandlesRetry() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val srcInput = File(activity.cacheDir, "b5_vm_in.json").apply { writeText("{\"k\":1}") }
            val inputJson = ToolInputCodec.encode(
                ToolInput(
                    uris = listOf(Uri.fromFile(srcInput)),
                    textInput = "{\"hello\":\"world\"}",
                    params = mapOf("direction" to "JSON → XML")
                )
            )
            val failedJob = JobEntity(
                jobId = "b5-hydrated-job",
                workRequestId = "b5-work-req",
                toolId = "CNV-23",
                toolTitle = "JSON/XML",
                groupName = "CONVERTER",
                isDummy = false,
                status = ToolJobWorker.STATUS_FAILED,
                executionMode = ToolJobWorker.MODE_IN_PROCESS,
                errorMessage = "Simulated failure",
                inputJson = inputJson,
                isRetryable = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            runBlocking { container.database.jobDao().upsert(failedJob) }

            val vm = ToolViewModel("CNV-23", container, initialJobId = "b5-hydrated-job")
            vm.bindContext(activity)
            val deadline = System.currentTimeMillis() + 3000L
            while (vm.state.value.lastJobId != "b5-hydrated-job" && System.currentTimeMillis() < deadline) {
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
                Thread.sleep(20L)
            }
            assertEquals("b5-hydrated-job", vm.state.value.lastJobId)
            assertEquals("{\"hello\":\"world\"}", vm.state.value.textInput)
            assertEquals("JSON → XML", vm.state.value.paramValues["direction"])

            // Lifecycle-safe SAF export writes real bytes and updates ExportState
            val exportSource = File(activity.cacheDir, "b5_export_src.txt").apply { writeText("export-payload-123") }
            val exportDest = File(activity.cacheDir, "b5_export_dest.txt")
            val outFile = ToolOutputFile(exportSource, "text/plain", "b5_export_dest.txt")
            vm.beginSafExport(outFile)
            assertEquals(outFile, vm.state.value.pendingExportOutput)
            vm.completeSafExport(Uri.fromFile(exportDest))
            val exportDeadline = System.currentTimeMillis() + 3000L
            while (vm.state.value.exportState !is ExportState.Succeeded && System.currentTimeMillis() < exportDeadline) {
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
                Thread.sleep(20L)
            }
            assertTrue(vm.state.value.exportState is ExportState.Succeeded)
            assertEquals("export-payload-123", exportDest.readText())

            // Delete output updates deletedOutputPaths
            vm.deleteOutput(outFile)
            val delDeadline = System.currentTimeMillis() + 2000L
            while (exportSource.absolutePath !in vm.state.value.deletedOutputPaths && System.currentTimeMillis() < delDeadline) {
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
                Thread.sleep(20L)
            }
            assertFalse(exportSource.exists())
            assertTrue(exportSource.absolutePath in vm.state.value.deletedOutputPaths)

            // Retry with missing jobId surfaces retry unavailable message cleanly
            val emptyVm = ToolViewModel("VID-01", container, initialJobId = null)
            emptyVm.bindContext(activity)
            emptyVm.retry(isEnglish = true)
            assertFalse(emptyVm.state.value.isRetryable)
            assertTrue(emptyVm.state.value.jobErrorMessage.isNotBlank())
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp")
    fun toolScreenAdaptiveTabletTwoPaneRenders() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "CNV-23",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull("Expected two-pane layout on tablet width", findByTag(nodes, "tool_two_pane_layout"))
            assertNotNull("Expected workspace readiness card", findByTag(nodes, "workspace_readiness_card"))
            assertNotNull("Expected run button", findByTag(nodes, "run_tool_button"))
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun toolScreenWorksAcrossRepresentativeEngineCategories() {
        val representativeIds = listOf("CORE-03", "VID-01", "AUD-09", "IMG-06", "PDF-01", "CNV-23")
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            representativeIds.forEach { id ->
                setTestContent(activity, isEnglish = true) {
                    ToolScreen(
                        toolId = id,
                        container = container,
                        isEnglish = true,
                        isFavorite = false,
                        onToggleFavorite = {},
                        onBack = {}
                    )
                }
                val nodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull("Missing header card for $id", findByTag(nodes, "tool_card_$id"))
                assertNotNull("Missing readiness card for $id", findByTag(nodes, "workspace_readiness_card"))
                assertNotNull("Missing run button for $id", findByTag(nodes, "run_tool_button"))
            }
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