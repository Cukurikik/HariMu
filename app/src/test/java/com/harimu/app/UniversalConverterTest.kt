package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.util.Log
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
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.harimu.app.core.converter.ConverterFormatMatrix
import com.harimu.app.core.converter.ConverterWorkflow
import com.harimu.app.core.converter.DetectedSource
import com.harimu.app.core.converter.SourceFormat
import com.harimu.app.core.converter.SourceFormatDetector
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.studios.UniversalConverterScreen
import com.harimu.app.feature.studios.UniversalConverterViewModel
import com.harimu.app.feature.toolscreen.ToolScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import org.robolectric.shadows.ShadowStatFs
import android.graphics.Bitmap
import com.harimu.app.core.engines.FontEngine
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.feature.toolscreen.ExportState
import kotlinx.coroutines.flow.toList
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class UniversalConverterTest {

    @Test
    fun workflowMappingCoversAll28ConverterToolsWithoutDuplicates() {
        val registryIds = ToolRegistry.byGroup(ToolGroup.CONVERTER).map { it.id }.toSet()
        val mapped = ConverterWorkflow.allMappedToolIds()
        assertEquals(28, registryIds.size)
        assertEquals("Every CNV-01..CNV-28 tool must belong to a ConverterWorkflow", registryIds, mapped)
        val totalAcrossWorkflows = ConverterWorkflow.entries.sumOf { it.toolIds.size }
        assertEquals("Workflows must partition the 28 converter tools cleanly", 28, totalAcrossWorkflows)
    }

    @Test
    fun sourceFormatDetectorSniffsMagicHeadersAndStructuredText() {
        fun sniff(name: String, bytes: ByteArray): SourceFormat =
            SourceFormatDetector.detectFromBytesAndName("file:///tmp/$name", name, bytes, bytes.size.toLong()).format

        assertEquals(SourceFormat.PDF, sniff("doc.bin", "%PDF-1.7\n".toByteArray()))
        assertEquals(
            SourceFormat.PNG,
            sniff("img.bin", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        )
        assertEquals(
            SourceFormat.JPG,
            sniff("photo.bin", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        )
        assertEquals(SourceFormat.WEBP, sniff("pic.bin", "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()))
        assertEquals(SourceFormat.WAV, sniff("snd.bin", "RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray()))
        assertEquals(SourceFormat.AVI, sniff("vid.bin", "RIFF\u0000\u0000\u0000\u0000AVI ".toByteArray()))
        assertEquals(SourceFormat.MP4, sniff("clip.bin", "\u0000\u0000\u0000\u0018ftypisom".toByteArray()))
        assertEquals(SourceFormat.HEIC, sniff("photo.bin", "\u0000\u0000\u0000\u0018ftypheic".toByteArray()))
        assertEquals(SourceFormat.FLAC, sniff("lossless.bin", "fLaC\u0000\u0000\u0000\u0022".toByteArray()))
        assertEquals(SourceFormat.OGG, sniff("audio.bin", "OggS\u0000\u0002".toByteArray()))
        assertEquals(
            SourceFormat.SEVEN_Z,
            sniff("arch.bin", byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))
        )
        assertEquals(SourceFormat.RAR, sniff("arch.bin", "Rar!\u001A\u0007\u0001".toByteArray()))
        assertEquals(SourceFormat.CBR, sniff("comic.cbr", "Rar!\u001A\u0007\u0001".toByteArray()))
        assertEquals(SourceFormat.TAR_GZ, sniff("bundle.tar.gz", byteArrayOf(0x1F, 0x8B.toByte(), 0x08)))
        assertEquals(SourceFormat.HZM, sniff("legacy.hzm", "HARIMUA1\u0000\u0001".toByteArray()))
        assertEquals(SourceFormat.DOCX, sniff("report.docx", "PK\u0003\u0004".toByteArray()))
        assertEquals(SourceFormat.EPUB, sniff("book.epub", "PK\u0003\u0004".toByteArray()))
        assertEquals(SourceFormat.ZIP, sniff("plain.zip", "PK\u0003\u0004".toByteArray()))
        assertEquals(SourceFormat.WOFF, sniff("ui.woff", "wOFF\u0000\u0001\u0000\u0000".toByteArray()))
        assertEquals(SourceFormat.WOFF2, sniff("ui.woff2", "wOF2\u0000\u0001\u0000\u0000".toByteArray()))
        assertEquals(SourceFormat.OTF, sniff("sans.otf", "OTTO\u0000\u000C".toByteArray()))
        assertEquals(SourceFormat.TTF, sniff("sans.ttf", byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x0C)))
        assertEquals(SourceFormat.SVG, sniff("icon.bin", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray()))
        assertEquals(SourceFormat.STL, sniff("part.stl", "solid cube\n  facet normal 0 0 1\n".toByteArray()))
        assertEquals(SourceFormat.TIFF, sniff("scan.tiff", ByteArray(0)))
        assertEquals(SourceFormat.PSD, sniff("design.psd", ByteArray(0)))
        assertEquals(SourceFormat.RAW_OTHER, sniff("camera.cr2", ByteArray(0)))

        assertEquals(SourceFormat.JSON, SourceFormatDetector.detectTextFormat("{\"a\":1}"))
        assertEquals(
            SourceFormat.GEOJSON,
            SourceFormatDetector.detectTextFormat("{\"type\":\"FeatureCollection\",\"features\":[]}")
        )
        assertEquals(SourceFormat.XML, SourceFormatDetector.detectTextFormat("<root><a/></root>"))
        assertEquals(SourceFormat.CSV, SourceFormatDetector.detectTextFormat("col1,col2\n1,2"))
        assertEquals(SourceFormat.TSV, SourceFormatDetector.detectTextFormat("col1\tcol2\n1\t2"))
        assertEquals(SourceFormat.CSS, SourceFormatDetector.detectTextFormat(".card { color: #fff; padding: 8px; }"))
        assertEquals(SourceFormat.MARKDOWN, SourceFormatDetector.detectTextFormat("# Heading\n- bullet"))
        assertEquals(
            SourceFormat.BASE64_IMAGE,
            SourceFormatDetector.detectTextFormat("data:image/png;base64,iVBORw0KGgo=")
        )
    }

    @Test
    fun formatMatrixShowsOnlyValidTargetsAndBlocksUnsupportedFormats() {
        // Unsupported codecs must produce zero format conversion targets + an explicit explanation
        listOf(SourceFormat.WOFF2, SourceFormat.TIFF, SourceFormat.PSD, SourceFormat.RAW_OTHER).forEach { unsupported ->
            assertTrue(
                "Expected no format targets for $unsupported",
                ConverterFormatMatrix.validFormatTargetsFor(unsupported).isEmpty()
            )
            assertNotNull(
                "Expected clear unsupportedReason for $unsupported",
                ConverterFormatMatrix.unsupportedReason(unsupported, isEnglish = true)
            )
        }

        // OTF only supports OTF -> WOFF 1.0, never subsetting or OTF -> TTF
        val otfTargets = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.OTF)
        assertEquals(listOf("otf_to_woff"), otfTargets.map { it.id })

        // DOCX only supports Basic Word -> PDF
        val docxTargets = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.DOCX)
        assertEquals(listOf("docx_pdf_basic"), docxTargets.map { it.id })

        // PDF OCR option matches PDF-15 parameter key ("language")
        val pdfTargets = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.PDF)
        val ocrTarget = pdfTargets.firstOrNull { it.id == "pdf_ocr_txt" }
        assertNotNull(ocrTarget)
        assertEquals(listOf("language"), ocrTarget!!.visibleParamKeys)
        assertTrue(pdfTargets.any { it.id == "pdf_docx_basic" && it.isLimitedOrBasic })

        // API gate: AVIF & HEIC require minimum platform decoders
        assertTrue(ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.AVIF, apiLevel = 30).isEmpty())
        assertTrue(ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.AVIF, apiLevel = 31).isNotEmpty())
        assertTrue(ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.HEIC, apiLevel = 27).isEmpty())
        assertTrue(ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.HEIC, apiLevel = 28).isNotEmpty())

        // Batch intersection: 2 PNGs keep image targets + security lock; PNG + PDF keeps ONLY security lock
        val png1 = DetectedSource("file:///a.png", "a.png", "png", 100L, SourceFormat.PNG, "Header")
        val png2 = DetectedSource("file:///b.png", "b.png", "png", 200L, SourceFormat.PNG, "Header")
        val pdf1 = DetectedSource("file:///c.pdf", "c.pdf", "pdf", 300L, SourceFormat.PDF, "Header")

        val twoPngTargets = ConverterFormatMatrix.validTargetsForSources(listOf(png1, png2))
        assertTrue(twoPngTargets.any { it.id == "img_webp" })
        assertTrue(twoPngTargets.any { it.id == "any_encrypt_zip" })

        val mixedTargets = ConverterFormatMatrix.validTargetsForSources(listOf(png1, pdf1))
        assertEquals(listOf("any_encrypt_zip"), mixedTargets.map { it.id })
    }

    @Test
    fun inputValidationChecksEnforceCryptoSpeechAndSubsetConstraints() {
        val wav = DetectedSource("file:///a.wav", "a.wav", "wav", 1024L, SourceFormat.WAV, "Header")
        val hugeWav = wav.copy(byteSize = 16L * 1024L * 1024L)
        val encryptTarget = ConverterFormatMatrix.securityTargetsFor(listOf(wav)).first()

        // Short password rejected for AES-256 ZIP creation
        val shortPwd = ConverterFormatMatrix.validateInputs(
            target = encryptTarget,
            sources = listOf(wav),
            textInput = "",
            params = mapOf("password" to "1234"),
            isEnglish = true
        )
        assertFalse(shortPwd.isValid)
        assertTrue(shortPwd.blockingError!!.contains("8"))

        val validPwd = ConverterFormatMatrix.validateInputs(
            target = encryptTarget,
            sources = listOf(wav),
            textInput = "",
            params = mapOf("password" to "StrongPass123"),
            isEnglish = true
        )
        assertTrue(validPwd.isValid)

        // CNV-07 15 MB audio cap enforced
        val sttTarget = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.WAV).first { it.toolId == "CNV-07" }
        val overLimit = ConverterFormatMatrix.validateInputs(
            target = sttTarget,
            sources = listOf(hugeWav),
            textInput = "",
            params = emptyMap(),
            isEnglish = true
        )
        assertFalse(overLimit.isValid)
        assertTrue(overLimit.blockingError!!.contains("15 MB"))

        // CNV-22 subset rejects OTF/WOFF2
        val ttfSubset = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.TTF).first { it.toolId == "CNV-22" }
        val otfSource = DetectedSource("file:///font.otf", "font.otf", "otf", 4096L, SourceFormat.OTF, "Header")
        val badSubset = ConverterFormatMatrix.validateInputs(
            target = ttfSubset,
            sources = listOf(otfSource),
            textInput = "",
            params = emptyMap(),
            isEnglish = true
        )
        assertFalse(badSubset.isValid)

        // Text mode blank check
        val jsonTarget = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.JSON).first { it.id == "json_xml" }
        assertFalse(
            ConverterFormatMatrix.validateInputs(jsonTarget, emptyList(), "   ", emptyMap(), isEnglish = true).isValid
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun compactUniversalConverterWorkflowsAndTextConversionQueueWork() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            try {
                var openedToolId: String? = null
                setTestContent(activity, isEnglish = true) {
                    UniversalConverterScreen(
                        container = container,
                        isEnglish = true,
                        onOpenTool = { openedToolId = it.id },
                        onBack = {}
                    )
                }

                val initialNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(initialNodes, "converter_screen"))
                assertNotNull(findByTag(initialNodes, "converter_mode_workbench"))
                assertNotNull(findByTag(initialNodes, "converter_mode_workflows"))

                // Verify Workflows view groups by 6 meaningful workflows with >= 48dp touch targets
                clickNode(findByTag(initialNodes, "converter_mode_workflows")!!)
                flushMainUi(activity)
                val workflowNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(workflowNodes, "converter_workflow_chip_ALL"))
                assertNotNull(findByTag(workflowNodes, "converter_workflow_chip_DATA_CODE"))
                val firstCard = scrollUntilTag(activity, "converter_tool_card_CNV-01")
                assertNotNull("Expected CNV-01 grouped inside Workflows view", firstCard)

                val density = activity.resources.displayMetrics.density
                listOf("converter_mode_workbench", "converter_mode_workflows", "converter_workflow_chip_ALL").forEach { tag ->
                    val node = findByTag(collectSemanticsNodes(activity.window.decorView), tag)!!
                    assertTrue(
                        "Touch target height for $tag must be >= 48dp",
                        node.boundsInRoot.height / density >= 47.5f
                    )
                }

                // Switch back to Workbench, load sample JSON, verify only valid JSON targets appear
                clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "converter_mode_workbench")!!)
                flushMainUi(activity)
                clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "converter_source_mode_text")!!)
                flushMainUi(activity)
                clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "converter_sample_JSON")!!)
                flushMainUi(activity)

                val jsonNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(jsonNodes, "converter_target_chip_json_xml"))
                assertNotNull(findByTag(jsonNodes, "converter_target_chip_json_pretty"))
                assertNull("XML->JSON must NOT be offered for JSON input", findByTag(jsonNodes, "converter_target_chip_xml_json"))
                assertNull("CSV->TSV must NOT be offered for JSON input", findByTag(jsonNodes, "converter_target_chip_csv_tsv"))

                // Enqueue JSON -> XML job and verify JobDao + Queue section displays it
                val runBtn = scrollUntilTag(activity, "converter_run_btn")
                assertNotNull(runBtn)
                clickNode(runBtn!!)
                flushMainUi(activity, frames = 16)

                val jobs = waitForJobs(container)
                assertTrue("Expected at least 1 converter job queued or finished", jobs.isNotEmpty())
                val firstJob = jobs.first()
                assertEquals("CNV-23", firstJob.toolId)

                val queueNode = scrollUntilTag(activity, "converter_job_card_${firstJob.jobId}")
                assertNotNull("Expected job card in converter queue section", queueNode)

                // Verify direct tool handoff button also invokes callback
                val openDirectBtn = scrollUntilTag(activity, "converter_open_tool_screen_btn")
                if (openDirectBtn != null) {
                    clickNode(openDirectBtn)
                    flushMainUi(activity)
                    assertEquals("CNV-23", openedToolId)
                }
            } finally {
                cleanupTestDb(container)
            }
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun expandedUniversalConverterShowsSideBySideQueueAndSupportsCancelAndBatchEnqueue() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            val pngA = File(activity.cacheDir, "batch_a.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            val pngB = File(activity.cacheDir, "batch_b.png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            try {
                // Seed an active QUEUED job to verify live queue cancel action on expanded layout
                val seededJob = JobEntity(
                    jobId = "conv-cancel-test",
                    workRequestId = "00000000-0000-0000-0000-000000000001",
                    toolId = "CNV-09",
                    toolTitle = "Universal Image Converter",
                    groupName = ToolGroup.CONVERTER.name,
                    isDummy = false,
                    status = ToolJobWorker.STATUS_QUEUED,
                    progressPercent = 15,
                    progressMessage = "Waiting...",
                    createdAt = System.currentTimeMillis()
                )
                runBlocking(Dispatchers.IO) {
                    container.database.jobDao().upsert(seededJob)
                }

                val vm = UniversalConverterViewModel(container)
                vm.bindContext(activity)

                setTestContent(activity, isEnglish = true) {
                    UniversalConverterScreen(
                        container = container,
                        isEnglish = true,
                        onOpenTool = {},
                        onBack = {}
                    )
                }

                val wideNodes = waitUntilNodes(activity) { nodes ->
                    findByTag(nodes, "converter_job_cancel_conv-cancel-test") != null
                }
                assertNotNull(findByTag(wideNodes, "converter_screen"))
                assertNotNull(findByTag(wideNodes, "converter_queue_section"))
                val cancelBtn = findByTag(wideNodes, "converter_job_cancel_conv-cancel-test")
                assertNotNull(cancelBtn)

                clickNode(cancelBtn!!)
                flushMainUi(activity, frames = 10)
                val updated = runBlocking(Dispatchers.IO) {
                    container.database.jobDao().getById("conv-cancel-test")
                }
                assertEquals(ToolJobWorker.STATUS_CANCELLED, updated?.status)

                // Verify batch ViewModel state on two PNGs exposes common image targets & batch flag
                vm.addFiles(listOf(Uri.fromFile(pngA), Uri.fromFile(pngB)))
                waitUntilVmState(vm) { it.sources.size == 2 }
                assertTrue(vm.state.value.availableTargets.any { it.id == "img_webp" })
                assertTrue(vm.state.value.batchSeparateJobs)
            } finally {
                pngA.delete()
                pngB.delete()
                cleanupTestDb(container)
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun homeAndCatalogAndToolScreenIntegrateWithUniversalConverter() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            try {
                // 1. Clicking Converter group card on Home opens UniversalConverterScreen
                val groupCard = scrollUntilTag(activity, "group_card_CONVERTER")
                assertNotNull("Expected group_card_CONVERTER on HomeScreen", groupCard)
                clickNode(groupCard!!)
                flushMainUi(activity, frames = 12)
                val converterNodes = waitUntilNodes(activity) { nodes ->
                    findByTag(nodes, "converter_screen") != null
                }
                assertNotNull("Home group card should navigate to UniversalConverterScreen", findByTag(converterNodes, "converter_screen"))

                // 2. CatalogScreen(CONVERTER) exposes catalog_open_converter_btn
                var catalogLaunchClicked = false
                setTestContent(activity, isEnglish = true) {
                    CatalogScreen(
                        initialGroup = ToolGroup.CONVERTER,
                        isEnglish = true,
                        favoriteIds = emptySet(),
                        onOpenTool = {},
                        onToggleFavorite = {},
                        onOpenUniversalConverter = { catalogLaunchClicked = true }
                    )
                }
                val catBtn = scrollUntilTag(activity, "catalog_open_converter_btn")
                assertNotNull(catBtn)
                clickNode(catBtn!!)
                flushMainUi(activity)
                assertTrue(catalogLaunchClicked)

                // 3. ToolScreen for CNV-21 exposes workbench shortcut & honest limitation notice
                var workbenchId: String? = null
                setTestContent(activity, isEnglish = true) {
                    ToolScreen(
                        toolId = "CNV-21",
                        container = container,
                        isEnglish = true,
                        isFavorite = false,
                        onToggleFavorite = {},
                        onBack = {},
                        onOpenUniversalConverter = { workbenchId = it }
                    )
                }
                val toolNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(toolNodes, "converter_honest_notice_card"))
                val wbBtn = findByTag(toolNodes, "tool_open_converter_btn")
                assertNotNull(wbBtn)
                clickNode(wbBtn!!)
                flushMainUi(activity)
                assertEquals("CNV-21", workbenchId)
            } finally {
                cleanupTestDb(container)
            }
        }
    }

    @Test
    fun formatIntegrityRejectsSpoofedContainersAndDetectsRawBase64BareGeoJsonAndLargeBinaryStl() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            val spoofedCsvJson = File(activity.cacheDir, "fake_json.csv").apply {
                writeText("""{"spoofed":true}""")
            }
            val spoofedCsvXml = File(activity.cacheDir, "fake_xml.csv").apply {
                writeText("<root><row/></root>")
            }
            val spoofedCsvTsv = File(activity.cacheDir, "fake_tsv.csv").apply {
                writeText("a\tb\n1\t2")
            }
            val invalidCss = File(activity.cacheDir, "bad.css").apply {
                writeText("just plain text without selectors or rules")
            }
            val docxSpoofedAsCbz = File(activity.cacheDir, "not_comic.cbz").apply {
                ZipOutputStream(FileOutputStream(this)).use { zip ->
                    zip.putNextEntry(ZipEntry("word/document.xml"))
                    zip.write("<w:document/>".toByteArray())
                    zip.closeEntry()
                }
            }
            val epubNamedZip = File(activity.cacheDir, "ebook_package.zip").apply {
                ZipOutputStream(FileOutputStream(this)).use { zip ->
                    zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                    zip.write("<container/>".toByteArray())
                    zip.closeEntry()
                }
            }
            val emptyZipNamedCbz = File(activity.cacheDir, "no_images.cbz").apply {
                ZipOutputStream(FileOutputStream(this)).use { zip ->
                    zip.putNextEntry(ZipEntry("readme.txt"))
                    zip.write("not a comic".toByteArray())
                    zip.closeEntry()
                }
            }
            try {
                val jsonCsv = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(spoofedCsvJson))
                assertEquals(SourceFormat.JSON, jsonCsv.format)
                assertTrue(jsonCsv.extensionMismatch)

                val xmlCsv = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(spoofedCsvXml))
                assertEquals(SourceFormat.XML, xmlCsv.format)
                assertTrue(xmlCsv.extensionMismatch)

                val tsvCsv = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(spoofedCsvTsv))
                assertEquals(SourceFormat.TSV, tsvCsv.format)
                assertTrue(tsvCsv.extensionMismatch)

                val badCss = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(invalidCss))
                assertEquals(SourceFormat.UNKNOWN, badCss.format)
                assertTrue(badCss.extensionMismatch)

                val docx = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(docxSpoofedAsCbz))
                assertEquals(SourceFormat.DOCX, docx.format)
                assertTrue(docx.extensionMismatch)

                val epub = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(epubNamedZip))
                assertEquals(SourceFormat.EPUB, epub.format)

                val fakeCbz = SourceFormatDetector.detectFromUri(activity, container.storage, Uri.fromFile(emptyZipNamedCbz))
                assertEquals(SourceFormat.ZIP, fakeCbz.format)
                assertTrue(fakeCbz.extensionMismatch)

                // Raw Base64 image payload without data: URI prefix
                val rawPngB64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
                assertEquals(SourceFormat.BASE64_IMAGE, SourceFormatDetector.detectTextFormat(rawPngB64))

                // Bare GeoJSON Geometry (Point) without FeatureCollection wrapper
                assertEquals(
                    SourceFormat.GEOJSON,
                    SourceFormatDetector.detectTextFormat("""{"type":"Point","coordinates":[106.8271,-6.1754]}""")
                )

                // Large binary STL (> 4096 header buffer) verified by 84 + 50 * triangles == total byteSize
                val triCount = 120
                val stlHeader = ByteArray(84)
                ByteBuffer.wrap(stlHeader).order(ByteOrder.LITTLE_ENDIAN).apply {
                    position(80)
                    putInt(triCount)
                }
                val detectedLargeStl = SourceFormatDetector.detectFromBytesAndName(
                    uriString = "file:///tmp/model.stl",
                    displayName = "model.stl",
                    headerBytes = stlHeader,
                    byteSize = 84L + 50L * triCount
                )
                assertEquals(SourceFormat.STL, detectedLargeStl.format)
            } finally {
                listOf(spoofedCsvJson, spoofedCsvXml, spoofedCsvTsv, invalidCss, docxSpoofedAsCbz, epubNamedZip, emptyZipNamedCbz)
                    .forEach { it.delete() }
                cleanupTestDb(container)
            }
        }
    }

    @Test
    fun b10TargetMatrixMatchesRealEngineCapabilitiesAndDefaults() {
        val wav8d = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.WAV).first { it.id == "aud_8d" }
        assertEquals("wav", wav8d.outputExt)
        assertEquals("8D WAV", wav8d.outputBadge)

        val flac8d = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.FLAC).first { it.id == "aud_8d" }
        assertEquals("flac", flac8d.outputExt)
        assertEquals("8D FLAC", flac8d.outputBadge)

        val opus8d = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.OPUS).first { it.id == "aud_8d" }
        assertEquals("mp3", opus8d.outputExt)

        val imgPdf = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.PNG).first { it.id == "img_pdf" }
        assertTrue("img_pdf must not expose unused quality slider", imgPdf.visibleParamKeys.isEmpty())

        val svgJpg = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.SVG).first { it.id == "svg_jpg" }
        assertEquals("Putih", svgJpg.defaultParams["bg"])

        val cnv13 = ToolRegistry.findById("CNV-13")
        assertNotNull(cnv13)
        assertTrue(cnv13!!.params.any { it.key == "target" })
    }

    @Test
    fun crossModeValidationParamIsolationBatchQueueAndMissingExportGuardWork() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            val pngA = File(activity.cacheDir, "b10_a.png").apply { writeBytes(makeTestPngBytes()) }
            val pngB = File(activity.cacheDir, "b10_b.png").apply { writeBytes(makeTestPngBytes()) }
            try {
                val vm = UniversalConverterViewModel(container)
                vm.bindContext(activity)

                // Text mode with valid JSON -> switching back to File mode with zero files must NOT validate stale textInput
                vm.setTextInput("""{"ok":true}""")
                assertTrue(vm.state.value.validate(isEnglish = true).isValid)
                vm.setTextMode(false)
                assertFalse(vm.state.value.validate(isEnglish = true).isValid)

                // Invalid CSS & truncated Base64 must fail validation
                vm.setTextInput(".card { color: red; }")
                assertTrue(vm.state.value.validate(isEnglish = true).isValid)
                val cssTarget = vm.state.value.selectedTarget!!
                assertFalse(
                    ConverterFormatMatrix.validateInputs(
                        target = cssTarget,
                        sources = emptyList(),
                        textInput = "not css",
                        params = emptyMap(),
                        isEnglish = true,
                        isTextMode = true
                    ).isValid
                )
                val b64Target = ConverterFormatMatrix.validFormatTargetsFor(SourceFormat.BASE64_IMAGE).first()
                assertFalse(
                    ConverterFormatMatrix.validateInputs(
                        target = b64Target,
                        sources = emptyList(),
                        textInput = "data:image/png;base64,@@@",
                        params = emptyMap(),
                        isEnglish = true,
                        isTextMode = true
                    ).isValid
                )

                // File mode with focused tool preference + cross-tool param isolation
                vm.focusToolOrWorkflow("CNV-11")
                assertEquals("CNV-11", vm.state.value.focusedToolId)
                vm.addFiles(listOf(Uri.fromFile(pngA), Uri.fromFile(pngB)))
                waitUntilVmState(vm) { it.sources.size == 2 }
                assertEquals("img_icons", vm.state.value.selectedTargetId)

                // Switch to Text mode with blank text -> must NOT fall back to stale file sources
                vm.setTextMode(true)
                assertFalse(vm.state.value.validate(isEnglish = true).isValid)
                vm.setTextMode(false)

                // Select AES-256 encrypt (CNV-18), set password, then switch to img_webp (CNV-09) -> password must not leak
                val encryptTarget = vm.state.value.availableTargets.first { it.id == "any_encrypt_zip" }
                vm.selectTarget(encryptTarget)
                vm.setParam("password", "SecretPass99")
                assertEquals("SecretPass99", vm.state.value.paramValues["password"])

                val webpTarget = vm.state.value.availableTargets.first { it.id == "img_webp" }
                vm.selectTarget(webpTarget)
                assertNull("Stale password from CNV-18 must be cleared when switching to CNV-09", vm.state.value.paramValues["password"])

                vm.clearFocusedTool()
                assertNull(vm.state.value.focusedToolId)

                // Batch enqueue on 2 PNGs must create 2 distinct CNV-09 jobs
                vm.enqueueConversion(isEnglish = true)
                val jobs = waitForJobsCount(container, expected = 2)
                assertEquals(2, jobs.count { it.toolId == "CNV-09" })

                // Missing export file must fail cleanly instead of launching SAF
                var safRequested = false
                val missingOutput = com.harimu.app.core.toolapi.ToolOutputFile(
                    file = File(activity.cacheDir, "does_not_exist.webp"),
                    mimeType = "image/webp",
                    displayName = "does_not_exist.webp"
                )
                vm.exportToDefaultFolderOrRequestSaf(missingOutput, "") { safRequested = true }
                assertFalse(safRequested)
                assertTrue(vm.state.value.exportState is ExportState.Failed)
            } finally {
                pngA.delete()
                pngB.delete()
                cleanupTestDb(container)
            }
        }
    }

    @Test
    fun representativeConverterToolsExecuteEndToEndWithoutFabrication() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            val ctx = ToolContext(activity.applicationContext, container.storage, isEnglish = true)
            val pngFile = File(activity.cacheDir, "e2e_sample.png").apply { writeBytes(makeTestPngBytes()) }
            val plainTxtFile = File(activity.cacheDir, "secret_note.txt").apply { writeText("HariMu B10 payload") }
            val stlFile = File(activity.cacheDir, "single_tri.stl").apply { writeBytes(makeBinaryStlBytes()) }
            val fontFile = File(activity.cacheDir, "minimal.ttf").apply {
                val head = ByteArray(54)
                val maxp = ByteArray(6).also { it[4] = 1 }
                val cmap = ByteArray(4)
                val sfnt = FontEngine.Sfnt(
                    0x00010000,
                    listOf(FontEngine.Table("head", head), FontEngine.Table("maxp", maxp), FontEngine.Table("cmap", cmap))
                )
                writeBytes(FontEngine.writeSfnt(sfnt))
            }
            try {
                runBlocking {
                    // CNV-23 JSON <-> XML
                    val cnv23 = ToolRegistry.findById("CNV-23")!!
                    val xmlOut = cnv23.run(ctx, ToolInput(textInput = """{"app":"HariMu","ok":true}""", params = mapOf("dir" to "JSON -> XML")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(xmlOut.textOutput!!.contains("<app>HariMu</app>"))

                    val jsonOut = cnv23.run(ctx, ToolInput(textInput = xmlOut.textOutput!!, params = mapOf("dir" to "XML -> JSON")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(jsonOut.textOutput!!.contains("\"HariMu\""))

                    // CNV-24 CSV <-> TSV
                    val cnv24 = ToolRegistry.findById("CNV-24")!!
                    val tsvOut = cnv24.run(ctx, ToolInput(textInput = "a,\"b,c\"\n1,2", params = mapOf("dir" to "CSV -> TSV")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(tsvOut.textOutput!!.contains("a\t"))
                    val csvBack = cnv24.run(ctx, ToolInput(textInput = tsvOut.textOutput!!, params = mapOf("dir" to "TSV -> CSV")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(csvBack.textOutput!!.contains("\"b,c\""))

                    // CNV-25 Pretty/Minify JSON + Pretty CSS
                    val cnv25 = ToolRegistry.findById("CNV-25")!!
                    val minJson = cnv25.run(ctx, ToolInput(textInput = "{\n  \"a\" : 1\n}", params = mapOf("mode" to "Minify JSON")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertEquals("""{"a":1}""", minJson.textOutput)
                    val prettyCss = cnv25.run(ctx, ToolInput(textInput = ".box{color:#fff;margin:4px;}", params = mapOf("mode" to "Pretty CSS")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(prettyCss.textOutput!!.contains("\n  color:#fff;"))

                    // CNV-26 Markdown -> HTML
                    val cnv26 = ToolRegistry.findById("CNV-26")!!
                    val htmlOut = cnv26.run(ctx, ToolInput(textInput = "# Title\n\nHello **HariMu**"))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(htmlOut.textOutput!!.contains("<h1>Title</h1>"))
                    assertTrue(htmlOut.textOutput!!.contains("<strong>HariMu</strong>"))

                    // CNV-27 Bare GeoJSON -> KML
                    val cnv27 = ToolRegistry.findById("CNV-27")!!
                    val kmlOut = cnv27.run(ctx, ToolInput(textInput = """{"type":"Point","coordinates":[106.8271,-6.1754]}"""))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(kmlOut.textOutput!!.contains("<Point><coordinates>106.8271,-6.1754,0.0</coordinates></Point>"))

                    // CNV-28 Binary STL -> OBJ
                    val cnv28 = ToolRegistry.findById("CNV-28")!!
                    val objOut = cnv28.run(ctx, ToolInput(uris = listOf(Uri.fromFile(stlFile))))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    val objText = objOut.outputs.single().file.readText()
                    assertTrue(objText.contains("v 0.0 0.0 0.0"))
                    assertTrue(objText.contains("f 1 2 3"))

                    // CNV-18 AES-256 encrypt -> decrypt + CNV-16 sanitize
                    val cnv18 = ToolRegistry.findById("CNV-18")!!
                    val encRes = cnv18.run(
                        ctx,
                        ToolInput(
                            uris = listOf(Uri.fromFile(plainTxtFile)),
                            params = mapOf("mode" to "Enkripsi ZIP AES-256", "password" to "StrongPass123")
                        )
                    ).toList().filterIsInstance<ToolProgress.Done>().single().result
                    val encZip = encRes.outputs.single().file
                    assertTrue(encZip.exists() && encZip.length() > 0)

                    val decRes = cnv18.run(
                        ctx,
                        ToolInput(
                            uris = listOf(Uri.fromFile(encZip)),
                            params = mapOf("mode" to "Dekripsi ZIP AES-256", "password" to "StrongPass123")
                        )
                    ).toList().filterIsInstance<ToolProgress.Done>().single().result
                    val plainZip = decRes.outputs.single().file
                    assertTrue(plainZip.exists() && plainZip.length() > 0)

                    val cnv16 = ToolRegistry.findById("CNV-16")!!
                    val cleanRes = cnv16.run(ctx, ToolInput(uris = listOf(Uri.fromFile(plainZip))))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result
                    assertTrue(cleanRes.outputs.single().file.exists())

                    // CNV-21 TTF -> WOFF -> TTF
                    val cnv21 = ToolRegistry.findById("CNV-21")!!
                    val woffOut = cnv21.run(ctx, ToolInput(uris = listOf(Uri.fromFile(fontFile)), params = mapOf("target" to "TTF/OTF → WOFF")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result.outputs.single().file
                    assertEquals("woff", woffOut.extension)
                    val ttfBack = cnv21.run(ctx, ToolInput(uris = listOf(Uri.fromFile(woffOut)), params = mapOf("target" to "WOFF → TTF/OTF")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result.outputs.single().file
                    assertEquals("ttf", ttfBack.extension)

                    // CNV-09 PNG -> WEBP & PNG -> PDF, plus CNV-12 Encode & Decode Base64
                    val cnv09 = ToolRegistry.findById("CNV-09")!!
                    val webpOut = cnv09.run(ctx, ToolInput(uris = listOf(Uri.fromFile(pngFile)), params = mapOf("format" to "WEBP", "quality" to 85f)))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result.outputs.single().file
                    assertEquals("RIFF", webpOut.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))

                    val pdfOut = cnv09.run(ctx, ToolInput(uris = listOf(Uri.fromFile(pngFile)), params = mapOf("format" to "PDF")))
                        .toList().filterIsInstance<ToolProgress.Done>().single().result.outputs.single().file
                    assertEquals("%PDF-", pdfOut.readBytes().copyOfRange(0, 5).toString(Charsets.US_ASCII))

                    val cnv12 = ToolRegistry.findById("CNV-12")!!
                    val b64Res = cnv12.run(
                        ctx,
                        ToolInput(uris = listOf(Uri.fromFile(pngFile)), params = mapOf("mode" to "Encode (gambar → Base64)", "dataUri" to true))
                    ).toList().filterIsInstance<ToolProgress.Done>().single().result
                    val b64String = b64Res.outputs.single().file.readText()
                    assertTrue(b64String.startsWith("data:image/"))

                    val decodedPng = cnv12.run(
                        ctx,
                        ToolInput(textInput = b64String, params = mapOf("mode" to "Decode (Base64 → gambar)"))
                    ).toList().filterIsInstance<ToolProgress.Done>().single().result.outputs.single().file
                    assertTrue(decodedPng.readBytes().copyOfRange(0, 4).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
                }
            } finally {
                listOf(pngFile, plainTxtFile, stlFile, fontFile).forEach { it.delete() }
                cleanupTestDb(container)
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun workflowsSearchFindsToolsByFormatKeywordsAndShowsFocusedBanner() {
        withMainActivity { activity ->
            val container = initTestWorkManagerAndClearDb(activity)
            try {
                setTestContent(activity, isEnglish = true) {
                    UniversalConverterScreen(
                        container = container,
                        isEnglish = true,
                        onOpenTool = {},
                        onBack = {}
                    )
                }

                clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "converter_mode_workflows")!!)
                flushMainUi(activity)

                // Search by format keyword "kml" -> finds CNV-27
                val searchNode = findByTag(collectSemanticsNodes(activity.window.decorView), "converter_search_input")!!
                searchNode.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(androidx.compose.ui.text.AnnotatedString("kml"))
                flushMainUi(activity, frames = 8)

                val kmlNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(kmlNodes, "converter_tool_card_CNV-27"))
                assertNull(findByTag(kmlNodes, "converter_tool_card_CNV-01"))

                // Clear search and search "woff" -> open in workbench -> focused banner visible & dismissible
                searchNode.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(androidx.compose.ui.text.AnnotatedString("woff"))
                flushMainUi(activity, frames = 8)
                val fontNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(fontNodes, "converter_tool_card_CNV-21"))

                clickNode(findByTag(fontNodes, "converter_tool_workbench_CNV-21")!!)
                flushMainUi(activity, frames = 8)

                val wbNodes = collectSemanticsNodes(activity.window.decorView)
                assertNotNull(findByTag(wbNodes, "converter_focused_tool_banner"))
                clickNode(findByTag(wbNodes, "converter_focused_tool_dismiss")!!)
                flushMainUi(activity, frames = 6)
                assertNull(findByTag(collectSemanticsNodes(activity.window.decorView), "converter_focused_tool_banner"))
            } finally {
                cleanupTestDb(container)
            }
        }
    }

    private fun makeTestPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.CYAN)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun makeBinaryStlBytes(): ByteArray {
        val bb = ByteBuffer.allocate(84 + 50).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(80)
        bb.putInt(1)
        // Normal
        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(1f)
        // v1 (0,0,0), v2 (1,0,0), v3 (0,1,0)
        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
        bb.putFloat(1f); bb.putFloat(0f); bb.putFloat(0f)
        bb.putFloat(0f); bb.putFloat(1f); bb.putFloat(0f)
        bb.putShort(0)
        return bb.array()
    }

    private fun waitForJobsCount(container: AppContainer, expected: Int): List<JobEntity> {
        repeat(40) {
            val all = runBlocking(Dispatchers.IO) { container.database.jobDao().observeAll().first() }
            if (all.size >= expected) return all
            ShadowLooper.idleMainLooper(25, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        return runBlocking(Dispatchers.IO) { container.database.jobDao().observeAll().first() }
    }

    private fun initTestWorkManagerAndClearDb(activity: MainActivity): AppContainer {
        val app = activity.application as HariMuApp
        val wmConfig = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, wmConfig)
        val container = app.container
        ShadowStatFs.registerStats(container.storage.outputsDir.absolutePath, 1024 * 1024, 512 * 1024, 512 * 1024)
        runBlocking(Dispatchers.IO) {
            container.database.clearAllTables()
        }
        return container
    }

    private fun cleanupTestDb(container: AppContainer) {
        runBlocking {
            withContext(Dispatchers.IO) {
                container.database.clearAllTables()
            }
        }
        container.storage.clearTempCache()
    }

    private fun waitForJobs(container: AppContainer): List<JobEntity> {
        repeat(40) {
            val all = runBlocking(Dispatchers.IO) { container.database.jobDao().observeAll().first() }
            if (all.isNotEmpty()) return all
            ShadowLooper.idleMainLooper(25, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        return runBlocking(Dispatchers.IO) { container.database.jobDao().observeAll().first() }
    }

    private fun waitUntilVmState(
        vm: UniversalConverterViewModel,
        predicate: (com.harimu.app.feature.studios.UniversalConverterUiState) -> Boolean
    ) {
        repeat(40) {
            if (predicate(vm.state.value)) return
            ShadowLooper.idleMainLooper(25, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
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
