package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
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
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ImageResultPreview
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.image.ImageLutPreviewCard
import com.harimu.app.core.engines.ImageLayerCompositor
import com.harimu.app.core.engines.LayerHistory
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import com.harimu.app.feature.studios.ImageMatrixViewModel
import com.harimu.app.feature.toolscreen.FilterPreviewCard
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class ImageAuditB8Test {

    // --------------------------------------------------------- Image Matrix

    @Test
    fun layerHistoryAllReferencedUrisTracksCurrentUndoAndRedo() {
        val history = LayerHistory()
        val imgA = ImageLayerCompositor.defaultImageLayer("i1", "file:///a.png")
        val imgB = ImageLayerCompositor.defaultImageLayer("i2", "file:///b.png")
        val txt = ImageLayerCompositor.defaultTextLayer("t1", "Hi")

        history.push(listOf(imgA))
        history.push(listOf(imgA, imgB))
        history.undo()
        assertEquals(setOf("file:///a.png", "file:///b.png"), history.allReferencedUris())

        history.push(listOf(txt))
        assertEquals(setOf("file:///a.png"), history.allReferencedUris())

        val blankTxt = ImageLayerCompositor.defaultTextLayer("t_blank", "")
        assertEquals("Text", blankTxt.title(isEnglish = true))
        assertEquals("Teks", blankTxt.title(isEnglish = false))
        assertEquals("Hi", ImageLayerCompositor.defaultTextLayer("t2", "Hi").title(isEnglish = true))
        assertEquals("Image", imgA.title(isEnglish = true))
        assertEquals("Gambar", imgA.title(isEnglish = false))
    }

    @Test
    fun imageMatrixUndoAfterDeleteRetainsBitmapForFullResExport() {
        val app = ApplicationProvider.getApplicationContext<HariMuApp>()
        val pngFile = createSolidPng(app.cacheDir, "matrix_red.png", 160, 160, Color.rgb(220, 40, 40))
        val uri = Uri.fromFile(pngFile)

        val vm = ImageMatrixViewModel(app.container, app)
        vm.setLanguage(true)
        vm.initialize()
        vm.addImage(uri)
        pumpUiLoops(app)

        assertEquals(1, vm.state.value.layers.size)
        val layerId = vm.state.value.layers.first().id

        vm.deleteLayer(layerId)
        assertTrue(vm.state.value.layers.isEmpty())

        // Bitmap must remain cached and unrecycled while layer exists in undo history
        val bitmapsField = ImageMatrixViewModel::class.java.getDeclaredField("bitmaps").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val cachedAfterDelete = bitmapsField.get(vm) as Map<String, Bitmap>
        val retainedBitmap = cachedAfterDelete[uri.toString()]
        assertNotNull("Deleted image layer bitmap must stay cached for undo", retainedBitmap)
        assertFalse("Deleted image layer bitmap must not be recycled while undoable", retainedBitmap!!.isRecycled)

        vm.undo()
        assertEquals(1, vm.state.value.layers.size)

        var exportedFile: File? = null
        vm.exportComposition(format = "PNG", quality = 100, trackExportDialog = true) { file, _ ->
            exportedFile = file
        }
        pumpUiLoops(app, rounds = 15)

        val out = exportedFile
        assertNotNull("Undo after delete must still export the image layer", out)
        assertTrue(out!!.exists() && out.length() > 0)
        try {
            assertDimensions(out, 1080, 1080)
            assertFalse("Retained bitmap should remain unrecycled after export", retainedBitmap.isRecycled)
        } finally {
            out.delete()
            pngFile.delete()
        }
    }

    @Test
    fun imageMatrixCancelExportStopsCleanly() {
        val app = ApplicationProvider.getApplicationContext<HariMuApp>()
        val vm = ImageMatrixViewModel(app.container, app)
        vm.setLanguage(true)
        vm.addText("Cancelable")
        vm.exportComposition(format = "PNG", quality = 90, trackExportDialog = true)
        vm.cancelExport()
        pumpUiLoops(app)
        assertFalse(vm.state.value.isExporting)
    }

    // ----------------------------------------------------- Image Forge tools

    @Test
    fun representativeImageForgeToolsProduceValidOutputs() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HariMuApp>()
        val ctx = ToolContext(app, app.container.storage, isEnglish = true)
        val src400 = createGradientPng(app.cacheDir, "b8_400x200.png", 400, 200)
        val srcUri = Uri.fromFile(src400)
        val sq128a = createGradientPng(app.cacheDir, "b8_128a.png", 128, 128)
        val sq128b = createSolidPng(app.cacheDir, "b8_128b.png", 128, 128, Color.rgb(40, 160, 220))

        try {
            // IMG-01 Crop Pro
            val cropRes = runTool("IMG-01", ctx, ToolInput(uris = listOf(srcUri), params = mapOf("ratio" to "1:1")))
            val cropFile = cropRes.outputs.single().file
            assertDimensions(cropFile, 200, 200)

            // IMG-02 Smart Resizer (maxEdge > 0 and maxEdge == 0 scale)
            val resizeEdge = runTool("IMG-02", ctx, ToolInput(uris = listOf(srcUri), params = mapOf("maxEdge" to 120)))
            assertDimensions(resizeEdge.outputs.single().file, 120, 60)
            val resizeScale = runTool("IMG-02", ctx, ToolInput(uris = listOf(srcUri), params = mapOf("maxEdge" to 0, "scale" to 50f)))
            assertDimensions(resizeScale.outputs.single().file, 200, 100)

            // IMG-07 Straightener
            val straight = runTool("IMG-07", ctx, ToolInput(uris = listOf(srcUri), params = mapOf("angle" to 5f)))
            assertTrue(straight.outputs.single().file.length() > 0)

            // IMG-09 Alignment with exact 128x128 inputs (regression check for preview recycle bug)
            val align = runTool("IMG-09", ctx, ToolInput(uris = listOf(Uri.fromFile(sq128a), Uri.fromFile(sq128b))))
            assertTrue(align.outputs.single().file.length() > 0)

            // IMG-26 Compressor
            val comp = runTool("IMG-26", ctx, ToolInput(uris = listOf(srcUri), params = mapOf("format" to "JPG", "targetKB" to 25f)))
            assertTrue(comp.outputs.single().file.length() > 0)

            // IMG-33 Filters
            val filt = runTool(
                "IMG-33",
                ctx,
                ToolInput(uris = listOf(srcUri), params = mapOf("preset" to "Vintage", "blur" to 10, "vignette" to 20))
            )
            assertDimensions(filt.outputs.single().file, 400, 200)

            // IMG-37 Shadow & Glow
            val shadow = runTool("IMG-37", ctx, ToolInput(uris = listOf(srcUri)))
            assertTrue(shadow.outputs.single().file.length() > 0)

            // IMG-39 LUT
            val lut = runTool(
                "IMG-39",
                ctx,
                ToolInput(uris = listOf(srcUri), params = mapOf("preset" to "Teal & Orange", "intensity" to 85))
            )
            assertDimensions(lut.outputs.single().file, 400, 200)

            // IMG-42 Reflection (verifies non-blank mode summary)
            val refl = runTool("IMG-42", ctx, ToolInput(uris = listOf(srcUri)))
            assertTrue(refl.summary.contains("Refleksi Bawah"))
            assertTrue(refl.outputs.single().file.length() > 0)

            // IMG-45 GIF Maker
            val gif = runTool(
                "IMG-45",
                ctx,
                ToolInput(uris = listOf(Uri.fromFile(sq128a), Uri.fromFile(sq128b)), params = mapOf("fps" to 4f, "maxWidth" to 128f))
            )
            val header = gif.outputs.single().file.inputStream().use {
                val b = ByteArray(6)
                it.read(b)
                String(b, Charsets.US_ASCII)
            }
            assertEquals("GIF89a", header)
        } finally {
            src400.delete()
            sq128a.delete()
            sq128b.delete()
        }
    }

    // --------------------------------------------------------- UI / Previews

    @Test
    @Config(qualifiers = "w411dp-h840dp")
    fun imagePreviewAndResultBeforeAfterTogglesWork() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val beforeFile = createSolidPng(activity.cacheDir, "ui_before.png", 160, 120, Color.BLUE)
            val afterFile = createSolidPng(activity.cacheDir, "ui_after.png", 160, 120, Color.GREEN)
            val beforeUri = Uri.fromFile(beforeFile)
            val output = ToolOutputFile(afterFile, "image/png", afterFile.name)

            try {
                // 1. FilterPreviewCard (IMG-33)
                setTestContent(activity, isEnglish = true) {
                    FilterPreviewCard(
                        imageUri = beforeUri,
                        preset = "Vintage",
                        brightness = 5f,
                        contrast = 1.1f,
                        saturation = 1.1f,
                        temperature = 0f,
                        blurRadius = 0,
                        vignette = 10
                    )
                }
                val filterNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(filterNodes, "img_filter_preview_card"))
                assertNotNull(findByTag(filterNodes, "filter_before_after_toggle"))
                val beforeChip = findByTag(filterNodes, "filter_compare_before")!!
                clickNode(beforeChip)
                flushMainUi(activity)
                assertEquals(true, findByTag(collectSemanticsNodes(activity.window.decorView), "filter_compare_before")?.config?.getOrNull(SemanticsProperties.Selected))

                // 2. ImageLutPreviewCard (IMG-39) with real imageUri
                setTestContent(activity, isEnglish = true) {
                    ImageLutPreviewCard(
                        mode = "Preset bawaan",
                        preset = "Teal & Orange",
                        intensityPct = 85,
                        isEnglish = true,
                        imageUri = beforeUri
                    )
                }
                flushMainUi(activity, frames = 8)
                val lutNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(lutNodes, "img_lut_preview_card"))
                assertNotNull(findByTag(lutNodes, "lut_before_after_toggle"))
                clickNode(findByTag(lutNodes, "lut_compare_before")!!)
                flushMainUi(activity)
                assertEquals(true, findByTag(collectSemanticsNodes(activity.window.decorView), "lut_compare_before")?.config?.getOrNull(SemanticsProperties.Selected))

                // 3. ImageResultPreview with output dimensions + Before/After toggle
                setTestContent(activity, isEnglish = true) {
                    ImageResultPreview(
                        output = output,
                        storage = container.storage,
                        modifier = Modifier,
                        beforeImageUri = beforeUri
                    )
                }
                flushMainUi(activity, frames = 8)
                val resNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(resNodes, "image_result_preview_card"))
                assertNotNull(findByTag(resNodes, "result_before_after_toggle"))
                val metaText = findByTag(resNodes, "result_image_meta_text")
                    ?.config
                    ?.getOrNull(SemanticsProperties.Text)
                    ?.joinToString("") { it.text }
                    .orEmpty()
                assertTrue("Meta should contain output pixel size, got: $metaText", metaText.contains("160×120"))

                clickNode(findByTag(resNodes, "result_compare_before")!!)
                flushMainUi(activity)
                assertEquals(true, findByTag(collectSemanticsNodes(activity.window.decorView), "result_compare_before")?.config?.getOrNull(SemanticsProperties.Selected))
            } finally {
                beforeFile.delete()
                afterFile.delete()
            }
        }
    }

    // -------------------------------------------------------------- helpers

    private suspend fun runTool(id: String, ctx: ToolContext, input: ToolInput): ToolResult {
        val tool = ToolRegistry.findById(id) ?: error("Tool $id not found")
        val events = tool.run(ctx, input).toList()
        val done = events.lastOrNull() as? ToolProgress.Done
            ?: error("Tool $id did not finish with Done: $events")
        return done.result
    }

    private fun assertDimensions(file: File, expectedW: Int, expectedH: Int) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        assertEquals("Width mismatch for ${file.name}", expectedW, opts.outWidth)
        assertEquals("Height mismatch for ${file.name}", expectedH, opts.outHeight)
    }

    private fun createSolidPng(dir: File, name: String, w: Int, h: Int, color: Int): File {
        val file = File(dir, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            bmp.eraseColor(color)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }
        return file
    }

    private fun createGradientPng(dir: File, name: String, w: Int, h: Int): File {
        val file = File(dir, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            val paint = android.graphics.Paint()
            for (y in 0 until h step 4) {
                paint.color = Color.rgb((y * 255 / h.coerceAtLeast(1)), 120, 200)
                canvas.drawRect(0f, y.toFloat(), w.toFloat(), (y + 4).toFloat(), paint)
            }
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bmp.recycle()
        }
        return file
    }

    private fun pumpUiLoops(app: HariMuApp, rounds: Int = 6) {
        repeat(rounds) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
            Thread.sleep(25)
        }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
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

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }
}
