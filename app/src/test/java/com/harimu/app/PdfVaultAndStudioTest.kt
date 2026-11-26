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
import com.harimu.app.core.design.pdf.PdfCapabilityMode
import com.harimu.app.core.design.pdf.pdfToolCapability
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.catalog.CatalogScreen
import com.harimu.app.feature.studios.PdfStudioScreen
import com.harimu.app.feature.studios.PdfStudioTab
import com.harimu.app.feature.studios.PdfStudioViewModel
import com.harimu.app.feature.studios.PdfVaultCategory
import com.harimu.app.feature.studios.PdfVaultScreen
import com.harimu.app.feature.studios.categorizePdfTool
import com.harimu.app.feature.studios.tabForPdfTool
import com.harimu.app.feature.toolscreen.ToolScreen
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class PdfVaultAndStudioTest {

    private lateinit var app: HariMuApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(app)
    }

    @Test
    fun pdfVaultCategorizesAll21ToolsAndHonestCapabilities() {
        val pdfTools = ToolRegistry.byGroup(ToolGroup.PDF)
        assertEquals(21, pdfTools.size)
        val expectedIds = (1..21).map { "PDF-%02d".format(it) }.toSet()
        assertEquals(expectedIds, pdfTools.map { it.id }.toSet())
        assertTrue(pdfTools.none { it.isStub })

        val grouped = PdfVaultCategory.entries.associateWith { cat ->
            pdfTools.filter { categorizePdfTool(it.id) == cat }
        }
        assertEquals(21, grouped.values.sumOf { it.size })
        PdfVaultCategory.entries.forEach { category ->
            assertTrue("Category $category should not be empty", grouped[category].orEmpty().isNotEmpty())
        }

        // Honest capability separation
        assertEquals(PdfCapabilityMode.BASIC_CONVERSION, pdfToolCapability("PDF-06"))
        assertEquals(PdfCapabilityMode.BASIC_CONVERSION, pdfToolCapability("PDF-07"))
        assertEquals(PdfCapabilityMode.BASIC_CONVERSION, pdfToolCapability("CNV-13"))
        assertEquals(PdfCapabilityMode.RASTERIZED, pdfToolCapability("PDF-09"))
        assertEquals(PdfCapabilityMode.RASTERIZED, pdfToolCapability("PDF-14"))
        assertEquals(PdfCapabilityMode.ON_DEVICE_OCR, pdfToolCapability("PDF-15"))
        assertEquals(PdfCapabilityMode.ON_DEVICE_OCR, pdfToolCapability("PDF-16"))
        assertEquals(PdfCapabilityMode.ON_DEVICE_OCR, pdfToolCapability("CNV-15"))
        assertEquals(PdfCapabilityMode.AI_GATEWAY, pdfToolCapability("PDF-17"))
        assertEquals(PdfCapabilityMode.LOSSLESS_OBJECT, pdfToolCapability("PDF-01"))
        assertEquals(PdfCapabilityMode.LOSSLESS_OBJECT, pdfToolCapability("PDF-10"))
        assertEquals(PdfCapabilityMode.LOSSLESS_OBJECT, pdfToolCapability("PDF-20"))

        // Studio tab routing
        assertEquals(PdfStudioTab.PAGES_MERGE, tabForPdfTool("PDF-01"))
        assertEquals(PdfStudioTab.SECURITY_REDACT, tabForPdfTool("PDF-14"))
        assertEquals(PdfStudioTab.FORMS_META, tabForPdfTool("PDF-21"))
        assertEquals(PdfStudioTab.COMPARE, tabForPdfTool("PDF-20"))
        assertEquals(PdfStudioTab.INSPECT, tabForPdfTool("PDF-15"))
    }

    @Test
    fun pdfVaultEngineInspectMergeSplitRotateReorderAndExtractRoundTrip() {
        val storage = app.container.storage
        val docA = File(app.cacheDir, "test_a.pdf")
        val docB = File(app.cacheDir, "test_b.pdf")
        createSamplePdf(
            file = docA,
            pageLines = listOf("Alpha Contract Page One", "Confidential Clause Two", "Appendix Three"),
            title = "Annual Contract",
            author = "HariMu QA"
        )
        createSamplePdf(
            file = docB,
            pageLines = listOf("Invoice Number 9901")
        )

        val inspection = PdfVaultEngine.inspectDocument(app, storage, Uri.fromFile(docA))
        assertEquals(3, inspection.pageCount)
        assertFalse(inspection.isEncrypted)
        assertFalse(inspection.requiresPassword)
        assertEquals("Annual Contract", inspection.title)
        assertEquals("HariMu QA", inspection.author)
        assertTrue(inspection.hasExtractableText)
        assertTrue(inspection.previewSnippet.contains("Alpha Contract"))

        // Merge (3 + 1 = 4 pages)
        val merged = File(app.cacheDir, "test_merged.pdf")
        val mergedCount = PdfVaultEngine.merge(
            app, storage, listOf(Uri.fromFile(docA), Uri.fromFile(docB)), merged
        )
        assertEquals(4, mergedCount)
        assertEquals(4, PdfVaultEngine.pageCount(app, storage, Uri.fromFile(merged)))

        // Split (2 pages per file -> 2 PDF entries in ZIP)
        val splitZip = File(app.cacheDir, "test_split.zip")
        val (_, parts) = PdfVaultEngine.split(app, storage, Uri.fromFile(merged), splitZip, pagesPerFile = 2)
        assertEquals(2, parts)
        ZipFile(splitZip).use { zip ->
            assertEquals(2, zip.size())
        }

        // Rotate page 2 by 90 deg
        val rotated = File(app.cacheDir, "test_rotated.pdf")
        val rotatedTargets = PdfVaultEngine.rotate(
            app, storage, Uri.fromFile(docA), rotated, angle = 90, pages = listOf(2)
        )
        assertEquals(1, rotatedTargets)
        PDDocument.load(rotated).use { pd ->
            assertEquals(0, pd.getPage(0).rotation)
            assertEquals(90, pd.getPage(1).rotation)
        }

        // Reorder [3, 1]
        val reordered = File(app.cacheDir, "test_reordered.pdf")
        val reorderedCount = PdfVaultEngine.reorder(
            app, storage, Uri.fromFile(docA), reordered, listOf(3, 1)
        )
        assertEquals(2, reorderedCount)
        val reorderedText = PdfVaultEngine.extractText(reordered)
        assertTrue(reorderedText.indexOf("Appendix Three") < reorderedText.indexOf("Alpha Contract"))

        // Extract [2]
        val extracted = File(app.cacheDir, "test_extracted.pdf")
        val extractedCount = PdfVaultEngine.extract(app, storage, Uri.fromFile(docA), extracted, listOf(2))
        assertEquals(1, extractedCount)
        val extractedText = PdfVaultEngine.extractText(extracted)
        assertTrue(extractedText.contains("Confidential Clause Two"))
        assertFalse(extractedText.contains("Alpha Contract"))
    }

    @Test
    fun pdfVaultEngineSecurityWatermarkMetadataFormAndRedactPreviewWork() {
        val storage = app.container.storage
        val src = File(app.cacheDir, "test_sec_src.pdf")
        createSamplePdf(
            file = src,
            pageLines = listOf("Client NIK: 3174009911", "Public Summary Page"),
            title = "Draft Doc",
            author = "Andika",
            withAcroFormField = "applicant_name" to "Initial",
            withAnnotation = true
        )

        // Metadata round-trip
        val metaOut = File(app.cacheDir, "test_meta.pdf")
        PdfVaultEngine.writeMetadata(
            app, storage, Uri.fromFile(src), metaOut,
            mapOf("title" to "Final Report", "author" to "HariMu", "subject" to "Finance")
        )
        val readMeta = PdfVaultEngine.readMetadata(app, storage, Uri.fromFile(metaOut))
        assertEquals("Final Report", readMeta["title"])
        assertEquals("HariMu", readMeta["author"])
        assertEquals("Finance", readMeta["subject"])

        // AcroForm list + fill
        val fieldsBefore = PdfVaultEngine.listFormFields(app, storage, Uri.fromFile(src))
        assertEquals(1, fieldsBefore.size)
        assertEquals("applicant_name", fieldsBefore.first().first)
        val formOut = File(app.cacheDir, "test_form.pdf")
        val filled = PdfVaultEngine.fillForm(
            app, storage, Uri.fromFile(src), formOut, mapOf("applicant_name" to "Budi Santoso")
        )
        assertEquals(1, filled)
        val fieldsAfter = PdfVaultEngine.listFormFields(app, storage, Uri.fromFile(formOut))
        assertEquals("Budi Santoso", fieldsAfter.first().second)

        // Page numbers
        val numOut = File(app.cacheDir, "test_num.pdf")
        val numPages = PdfVaultEngine.addPageNumbers(
            app, storage, Uri.fromFile(src), numOut, "{n}/{total}", 1, "Tengah Bawah", 4
        )
        assertEquals(2, numPages)
        assertTrue(PdfVaultEngine.extractText(numOut).contains("1/2"))

        // Text watermark & object removal
        val wmOut = File(app.cacheDir, "test_wm.pdf")
        PdfVaultEngine.addTextWatermark(app, storage, Uri.fromFile(src), wmOut, "RAHASIA", 30, -35, 10)
        assertTrue(PdfVaultEngine.extractText(wmOut).contains("RAHASIA"))

        val unwmOut = File(app.cacheDir, "test_unwm.pdf")
        val removedAnnots = PdfVaultEngine.removeWatermarkObjects(app, storage, Uri.fromFile(src), unwmOut)
        // src contains 1 text annotation + 1 AcroForm widget annotation
        assertEquals(2, removedAnnots)

        // Encrypt (AES-128 & RC4-128) -> inspect requiresPassword -> decrypt
        val encOut = File(app.cacheDir, "test_enc.pdf")
        PdfVaultEngine.encrypt(
            app, storage, Uri.fromFile(src), encOut,
            userPassword = "secret123",
            ownerPassword = "owner456",
            keyBits = 128,
            preferAes = true
        )
        assertTrue(PdfVaultEngine.isEncrypted(app, storage, Uri.fromFile(encOut)))
        val lockedInspection = PdfVaultEngine.inspectDocument(app, storage, Uri.fromFile(encOut))
        assertTrue(lockedInspection.requiresPassword)
        val unlockedInspection = PdfVaultEngine.inspectDocument(
            app, storage, Uri.fromFile(encOut), password = "secret123"
        )
        assertFalse(unlockedInspection.requiresPassword)
        assertEquals(2, unlockedInspection.pageCount)

        val decOut = File(app.cacheDir, "test_dec.pdf")
        PdfVaultEngine.decrypt(app, storage, Uri.fromFile(encOut), decOut, "secret123")
        assertFalse(PdfVaultEngine.isEncrypted(app, storage, Uri.fromFile(decOut)))

        // Redaction preview hit scan works even on unlocked encrypted doc
        val redactPreview = PdfVaultEngine.previewRedactionHits(
            app, storage, Uri.fromFile(encOut), terms = listOf("3174009911"), password = "secret123"
        )
        assertTrue(redactPreview.totalBoxes >= 1)
        assertTrue(1 in redactPreview.pagesWithHits)
        assertEquals(2, redactPreview.pageSizes.size)
    }

    @Test
    fun pdfStudioViewModelHandlesTabsRotationOcrQueueAndValidation() {
        val vm = PdfStudioViewModel(app.container, app, PdfStudioTab.PAGES_MERGE)
        assertEquals(PdfStudioTab.PAGES_MERGE, vm.state.value.activeTab)

        vm.setTab(PdfStudioTab.SECURITY_REDACT)
        assertEquals(PdfStudioTab.SECURITY_REDACT, vm.state.value.activeTab)

        vm.bumpRotationPreview(90)
        assertEquals(90, vm.state.value.previewRotationDeg)
        vm.bumpRotationPreview(270)
        assertEquals(0, vm.state.value.previewRotationDeg)

        vm.setSplitPerFile(3)
        assertEquals(3, vm.state.value.splitPerFile)

        vm.setOcrLanguage("English")
        assertEquals("English", vm.state.value.ocrLanguageLabel)

        vm.setWatermarkOpacity(45)
        vm.setWatermarkAngle(-45)
        assertEquals(45, vm.state.value.watermarkOpacityPct)
        assertEquals(-45, vm.state.value.watermarkAngleDeg)

        // Blank redaction terms clear preview without errors
        vm.setRedactTerms("   ")
        vm.scanRedactionMatches()
        assertEquals(null, vm.state.value.redactPreview)

        // Merging fewer than 2 files surfaces a clean failure instead of throwing
        vm.mergeQueuedPdfs(isEnglish = true)
        repeat(20) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            if (vm.state.value.progress is ToolProgress.Failed) return@repeat
            Thread.sleep(50)
        }
        val progress = vm.state.value.progress
        assertTrue(progress is ToolProgress.Failed)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun pdfVaultScreenFiltersCategoriesAndNavigatesToStudio() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            var openedTab: PdfStudioTab? = null
            var openedToolId: String? = "INIT"

            setTestContent(activity, isEnglish = true) {
                PdfVaultScreen(
                    container = container,
                    isEnglish = true,
                    onOpenTool = {},
                    onOpenPdfStudio = { tab, id ->
                        openedTab = tab
                        openedToolId = id
                    },
                    onBack = {}
                )
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)
            val tags = nodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertNotNull("pdf_vault_screen missing; tags=$tags", findByTag(nodes, "pdf_vault_screen"))
            assertNotNull("pdf_vault_open_studio_btn missing; tags=$tags", findByTag(nodes, "pdf_vault_open_studio_btn"))
            assertNotNull("pdf_vault_search_input missing; tags=$tags", findByTag(nodes, "pdf_vault_search_input"))
            assertNotNull("pdf_filter_ALL missing; tags=$tags", findByTag(nodes, "pdf_filter_ALL"))
            assertNotNull("pdf_filter_SECURITY_PRIVACY missing; tags=$tags", findByTag(nodes, "pdf_filter_SECURITY_PRIVACY"))

            // Clicking Hero Studio CTA triggers onOpenPdfStudio
            clickNode(findByTag(nodes, "pdf_vault_open_studio_btn")!!)
            flushMainUi(activity)
            assertEquals(PdfStudioTab.INSPECT, openedTab)
            assertEquals(null, openedToolId)

            // Filter by Security & Privacy and verify PDF-14 card is visible
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "pdf_filter_SECURITY_PRIVACY")!!)
            flushMainUi(activity)
            val filteredNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(filteredNodes, "pdf_tool_card_PDF-14"))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h840dp")
    fun pdfStudioScreenCompactSwitchesAll5Tabs() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            setTestContent(activity, isEnglish = true) {
                PdfStudioScreen(
                    container = container,
                    isEnglish = true,
                    initialTabName = PdfStudioTab.INSPECT.name,
                    onOpenTool = {},
                    onBack = {}
                )
            }

            val initialNodes = collectSemanticsNodes(activity.window.decorView)
            val tags = initialNodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertNotNull("compact missing; tags=$tags", findByTag(initialNodes, "pdf_studio_compact_workspace"))
            assertNotNull("empty_preview missing; tags=$tags", findByTag(initialNodes, "pdf_studio_empty_preview"))
            assertNotNull("inspect missing; tags=$tags", findByTag(initialNodes, "pdf_studio_inspect_workspace"))

            val tabToWorkspace = listOf(
                PdfStudioTab.PAGES_MERGE to "pdf_studio_pages_workspace",
                PdfStudioTab.SECURITY_REDACT to "pdf_studio_security_workspace",
                PdfStudioTab.FORMS_META to "pdf_studio_forms_workspace",
                PdfStudioTab.COMPARE to "pdf_studio_compare_workspace",
                PdfStudioTab.INSPECT to "pdf_studio_inspect_workspace"
            )
            tabToWorkspace.forEach { (tab, workspaceTag) ->
                val tabBtn = findByTag(collectSemanticsNodes(activity.window.decorView), "pdf_studio_tab_${tab.name}")
                assertNotNull("Missing tab ${tab.name}", tabBtn)
                clickNode(tabBtn!!)
                flushMainUi(activity, frames = 4)
                assertNotNull(
                    "Expected workspace $workspaceTag after switching to ${tab.name}",
                    scrollUntilTag(activity, workspaceTag)
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h700dp")
    fun pdfStudioWideCatalogCtasAndToolScreenHonestNoticesRender() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container

            // 1. Wide PdfStudioScreen uses side-by-side workspace
            setTestContent(activity, isEnglish = true) {
                PdfStudioScreen(
                    container = container,
                    isEnglish = true,
                    onOpenTool = {},
                    onBack = {}
                )
            }
            val wideNodes = collectSemanticsNodes(activity.window.decorView)
            val wideTags = wideNodes.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertNotNull("wide missing; tags=$wideTags", findByTag(wideNodes, "pdf_studio_wide_workspace"))

            // 2. CatalogScreen for PDF exposes both PDF Vault & PDF Studio CTAs
            setTestContent(activity, isEnglish = true) {
                CatalogScreen(
                    initialGroup = ToolGroup.PDF,
                    isEnglish = true,
                    favoriteIds = emptySet(),
                    onOpenTool = {},
                    onToggleFavorite = {},
                    onBack = {},
                    onOpenPdfVault = {},
                    onOpenPdfStudio = {}
                )
            }
            val catalogNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(catalogNodes, "catalog_open_pdf_vault_btn"))
            assertNotNull(findByTag(catalogNodes, "catalog_open_pdf_studio_btn"))

            // 3. ToolScreen renders honest notice & studio link for PDF-06 (Basic OOXML), PDF-13, PDF-14
            listOf("PDF-06", "PDF-13", "PDF-14").forEach { toolId ->
                setTestContent(activity, isEnglish = true) {
                    ToolScreen(
                        toolId = toolId,
                        container = container,
                        isEnglish = true,
                        isFavorite = false,
                        onToggleFavorite = {},
                        onBack = {},
                        onOpenPdfStudio = { _, _ -> }
                    )
                }
                assertNotNull("Missing Studio button for $toolId", scrollUntilTag(activity, "tool_open_pdf_studio_btn"))
                assertNotNull("Missing honest notice for $toolId", scrollUntilTag(activity, "pdf_honest_notice_card"))
            }

            // 4. ToolScreen renders OCR readiness banner for PDF-15
            setTestContent(activity, isEnglish = true) {
                ToolScreen(
                    toolId = "PDF-15",
                    container = container,
                    isEnglish = true,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = {},
                    onOpenPdfStudio = { _, _ -> }
                )
            }
            assertNotNull(scrollUntilTag(activity, "pdf_ocr_readiness_card"))
        }
    }

    private fun createSamplePdf(
        file: File,
        pageLines: List<String>,
        title: String? = null,
        author: String? = null,
        withAcroFormField: Pair<String, String>? = null,
        withAnnotation: Boolean = false
    ) {
        PDDocument().use { doc ->
            title?.let { doc.documentInformation.title = it }
            author?.let { doc.documentInformation.author = it }
            pageLines.forEachIndexed { idx, line ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(56f, 760f)
                    stream.showText(line)
                    stream.endText()
                }
                if (withAnnotation && idx == 0) {
                    val annot = PDAnnotationText().apply {
                        rectangle = PDRectangle(50f, 700f, 30f, 30f)
                        contents = "Watermark annotation"
                    }
                    page.annotations.add(annot)
                }
            }
            if (withAcroFormField != null && doc.numberOfPages > 0) {
                val acroForm = PDAcroForm(doc)
                val resources = PDResources()
                resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
                acroForm.defaultResources = resources
                doc.documentCatalog.acroForm = acroForm
                val textField = PDTextField(acroForm).apply {
                    partialName = withAcroFormField.first
                    defaultAppearance = "/Helv 12 Tf 0 g"
                }
                acroForm.fields.add(textField)
                textField.widgets.firstOrNull()?.let { widget ->
                    widget.rectangle = PDRectangle(56f, 700f, 220f, 24f)
                    widget.page = doc.getPage(0)
                    doc.getPage(0).annotations.add(widget)
                }
                textField.setValue(withAcroFormField.second)
            }
            doc.save(file)
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
