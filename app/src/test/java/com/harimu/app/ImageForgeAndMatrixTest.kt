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
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.engines.ImageFormatMatrix
import com.harimu.app.core.engines.ImageGeometryMath
import com.harimu.app.core.engines.ImageLutEngine
import com.harimu.app.core.engines.MatrixLayerKind
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.studios.ImageForgeCategory
import com.harimu.app.feature.studios.ImageForgeScreen
import com.harimu.app.feature.studios.ImageMatrixScreen
import com.harimu.app.feature.studios.ImageMatrixViewModel
import com.harimu.app.feature.studios.categorizeImageTool
import com.harimu.app.feature.toolscreen.ToolScreen
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class ImageForgeAndMatrixTest {

    @Test
    fun imageForgeCategorizesAll47ToolsWithoutOverlapOrMissingIds() {
        val imageTools = ToolRegistry.byGroup(ToolGroup.IMAGE)
        assertEquals(47, imageTools.size)

        val expectedIds = (1..47).map { "IMG-${"%02d".format(it)}" }.toSet()
        assertEquals(expectedIds, imageTools.map { it.id }.toSet())

        val grouped = ImageForgeCategory.entries.associateWith { cat ->
            imageTools.filter { categorizeImageTool(it.id) == cat }
        }
        assertEquals(47, grouped.values.sumOf { it.size })
        ImageForgeCategory.entries.forEach { category ->
            assertTrue("Category $category should not be empty", grouped[category].orEmpty().isNotEmpty())
        }

        assertEquals(DedicatedScreenRoute.IMAGE_MATRIX, ToolRegistry.findById("IMG-47")?.dedicatedRoute)
    }

    @Test
    fun imageEngineMathAndFormatMatrixInvariantsHold() {
        val crop = ImageGeometryMath.cropRectForRatio(
            viewW = 1600,
            viewH = 900,
            ratioW = 1f,
            ratioH = 1f,
            zoomPercent = 100f,
            offsetXPct = 0f,
            offsetYPct = 0f
        )
        assertEquals(900, crop.width)
        assertEquals(900, crop.height)
        assertEquals(350, crop.left)

        val safe = ImageGeometryMath.straightenSafeRect(1000, 800, 10f)
        assertTrue(safe.width in 1..1000)
        assertTrue(safe.height in 1..800)

        val lut = ImageLutEngine.preset("Teal & Orange")
        val graded = lut.lookupNormalized(0.8f, 0.5f, 0.2f)
        assertEquals(3, graded.size)
        assertTrue(graded[0] > 0.8f)

        val encodable = ImageFormatMatrix.encodeLabels
        assertTrue("JPG" in encodable && "PNG" in encodable && "WebP" in encodable)
        assertFalse("SVG" in encodable)
    }

    @Test
    fun imageMatrixViewModelSupportsLayerCrudUndoRedoAndTransformHelpers() {
        val app = ApplicationProvider.getApplicationContext<HariMuApp>()
        val vm = ImageMatrixViewModel(app.container, app)
        vm.initialize()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        vm.addText("HariMu Poster")
        assertEquals(1, vm.state.value.layers.size)
        val textLayerId = vm.state.value.selectedLayerId!!
        assertEquals(MatrixLayerKind.TEXT, vm.state.value.selectedLayer?.kind)
        assertTrue(vm.state.value.canUndo)

        vm.addSticker("🔥")
        assertEquals(2, vm.state.value.layers.size)

        vm.moveSelectedBy(-10f, 5f)
        vm.commitLayers()
        val movedLayer = vm.state.value.selectedLayer!!
        assertEquals(64f, movedLayer.xPct, 0.01f)
        assertEquals(29f, movedLayer.yPct, 0.01f)

        vm.centerLayer(textLayerId)
        val centeredText = vm.state.value.layers.first { it.id == textLayerId }
        assertEquals(50f, centeredText.xPct, 0.01f)
        assertEquals(50f, centeredText.yPct, 0.01f)

        vm.undo()
        val restoredText = vm.state.value.layers.first { it.id == textLayerId }
        assertEquals(82f, restoredText.yPct, 0.01f)
        assertTrue(vm.state.value.canRedo)

        vm.redo()
        assertEquals(50f, vm.state.value.layers.first { it.id == textLayerId }.yPct, 0.01f)

        vm.clearCanvas()
        assertTrue(vm.state.value.layers.isEmpty())
        vm.undo()
        assertEquals(2, vm.state.value.layers.size)
    }

    @Test
    fun imageMatrixViewModelRejectsEmptyExportCleanly() {
        val app = ApplicationProvider.getApplicationContext<HariMuApp>()
        val vm = ImageMatrixViewModel(app.container, app)
        var doneMessage: String? = null
        vm.exportComposition(format = "JPG", quality = 90, trackExportDialog = true) { _, msg ->
            doneMessage = msg
        }
        assertNotNull(doneMessage)
        assertTrue(vm.state.value.exportProgress is ToolProgress.Failed)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun imageForgeScreenFiltersCategoriesAndSearchesAllImageTools() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            var openedMatrix = false

            setTestContent(activity, isEnglish = true) {
                ImageForgeScreen(
                    container = container,
                    isEnglish = true,
                    onOpenTool = {},
                    onOpenImageMatrix = { openedMatrix = true },
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(nodes, "image_forge_screen"))
            assertNotNull(findByTag(nodes, "image_forge_open_matrix_btn"))
            assertNotNull(findByTag(nodes, "image_forge_search_input"))
            assertNotNull(findByTag(nodes, "image_filter_ALL"))
            assertNotNull(findByTag(nodes, "image_filter_GEOMETRY_CROP"))

            clickNode(findByTag(nodes, "image_forge_open_matrix_btn")!!)
            flushMainUi(activity)
            assertTrue(openedMatrix)

            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "image_filter_BATCH_ANIMATION")!!)
            flushMainUi(activity)
            val afterFilter = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(afterFilter, "image_tool_card_IMG-45"))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h840dp")
    fun imageMatrixScreenCompactWorkspaceHandlesAddTextTabSwitchAndExportDialog() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            setTestContent(activity, isEnglish = true) {
                ImageMatrixScreen(
                    container = container,
                    isEnglish = true,
                    onBack = {}
                )
            }

            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(initialNodes, "matrix_compact_workspace"))
            assertNotNull(findByTag(initialNodes, "matrix_add_image_btn"))
            assertNotNull(findByTag(initialNodes, "matrix_add_text_btn"))
            assertNotNull(findByTag(initialNodes, "matrix_add_sticker_btn"))

            // Open Add Text dialog and create a layer
            clickNode(findByTag(initialNodes, "matrix_add_text_btn")!!)
            flushMainUi(activity)
            val textDialogNodes = collectAllWindowSemanticsNodes(activity)
            val textField = findByTag(textDialogNodes, "matrix_text_dialog_input")
            assertNotNull(textField)
            textField!!.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(
                androidx.compose.ui.text.AnnotatedString("Banner Sale")
            )
            flushMainUi(activity)
            val confirmBtn = findByTag(collectAllWindowSemanticsNodes(activity), "matrix_text_dialog_confirm")!!
            clickNode(confirmBtn)
            flushMainUi(activity, frames = 8)

            // Transform inspector appears automatically with Center & Reset actions
            val updatedNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(updatedNodes, "matrix_transform_inspector"))
            assertNotNull(findByTag(updatedNodes, "matrix_center_layer_btn"))

            // Switch to Canvas tab and verify preset chips
            clickNode(findByTag(updatedNodes, "matrix_tab_canvas")!!)
            flushMainUi(activity)
            assertNotNull(findByTag(collectSemanticsNodes(activity.window.decorView), "matrix_canvas_inspector"))

            // Open Export dialog and verify PNG, JPG, WEBP choices
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "matrix_top_export_btn")!!)
            flushMainUi(activity, frames = 8)
            val exportNodes = collectAllWindowSemanticsNodes(activity)
            assertNotNull(findByTag(exportNodes, "matrix_export_fmt_PNG"))
            assertNotNull(findByTag(exportNodes, "matrix_export_fmt_JPG"))
            assertNotNull(findByTag(exportNodes, "matrix_export_fmt_WEBP"))
            assertNotNull(findByTag(exportNodes, "matrix_export_start_btn"))
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h700dp")
    fun imageMatrixScreenWideWorkspaceAndCatalogCtaAndToolPreviewsRender() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // 1. Wide ImageMatrixScreen uses side-by-side workspace
            setTestContent(activity, isEnglish = true) {
                ImageMatrixScreen(container = container, isEnglish = true, onBack = {})
            }
            assertNotNull(findByTag(collectSemanticsNodes(activity.window.decorView), "matrix_wide_workspace"))

            // 2. CatalogScreen for IMAGE exposes Image Forge & Image Matrix CTAs
            setTestContent(activity, isEnglish = true) {
                CatalogScreen(
                    initialGroup = ToolGroup.IMAGE,
                    isEnglish = true,
                    favoriteIds = emptySet(),
                    onOpenTool = {},
                    onToggleFavorite = {},
                    onBack = {},
                    onOpenImageForge = {},
                    onOpenImageMatrix = {}
                )
            }
            val catalogNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(catalogNodes, "catalog_open_image_forge_btn"))
            assertNotNull(findByTag(catalogNodes, "catalog_open_image_matrix_btn"))

            // 3. ToolScreen renders specialized cards for IMG-23, IMG-35, IMG-39
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-23",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            assertNotNull(scrollUntilTag(activity, "img_format_support_card"))

            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-35",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            assertNotNull(scrollUntilTag(activity, "img_poster_preview_card"))

            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "IMG-39",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {}
                )
            }
            assertNotNull(scrollUntilTag(activity, "img_lut_preview_card"))
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
