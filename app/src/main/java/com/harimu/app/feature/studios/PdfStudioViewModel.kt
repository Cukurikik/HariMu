package com.harimu.app.feature.studios

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.pdf.PdfQueueItem
import com.harimu.app.core.pdf.PdfOcrEngine
import com.harimu.app.core.pdf.PdfRenderUtils
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class PdfStudioTab(val titleRes: Int) {
    INSPECT(R.string.pdf_studio_tab_inspect),
    PAGES_MERGE(R.string.pdf_studio_tab_pages),
    SECURITY_REDACT(R.string.pdf_studio_tab_security),
    FORMS_META(R.string.pdf_studio_tab_forms),
    COMPARE(R.string.pdf_studio_tab_compare)
}

fun tabForPdfTool(toolId: String?): PdfStudioTab = when (toolId) {
    "PDF-01", "PDF-02", "PDF-03", "PDF-04", "PDF-05", "PDF-08" -> PdfStudioTab.PAGES_MERGE
    "PDF-10", "PDF-11", "PDF-12", "PDF-13", "PDF-14" -> PdfStudioTab.SECURITY_REDACT
    "PDF-18", "PDF-19", "PDF-21" -> PdfStudioTab.FORMS_META
    "PDF-20" -> PdfStudioTab.COMPARE
    else -> PdfStudioTab.INSPECT
}

data class PdfStudioUiState(
    val activeTab: PdfStudioTab = PdfStudioTab.INSPECT,
    val primaryUri: Uri? = null,
    val primaryName: String = "",
    val unlockPassword: String = "",
    val inspection: PdfVaultEngine.PdfDocInspection? = null,
    val previewPageIndex: Int = 0,
    val isInspecting: Boolean = false,
    val docError: String? = null,
    val extractedText: String = "",
    // Inspect / OCR / Compress
    val ocrLanguageLabel: String = "Indonesia",
    val ocrDataInstalled: Boolean = false,
    val compressDpi: Int = 150,
    val compressQuality: Int = 70,
    // Pages / Merge / Image2PDF
    val selectedPages: List<Int> = emptyList(),
    val pageOrder: List<Int> = emptyList(),
    val previewRotationDeg: Int = 0,
    val splitPerFile: Int = 1,
    val mergeQueue: List<PdfQueueItem> = emptyList(),
    val imageQueue: List<Uri> = emptyList(),
    val imagePageSizeMode: String = "Fit",
    // Security / Watermark / Redaction
    val encryptUserPwd: String = "",
    val encryptOwnerPwd: String = "",
    val encryptKeyAlgo: String = "AES-256",
    val watermarkIsImage: Boolean = false,
    val watermarkText: String = "RAHASIA",
    val watermarkImageUri: Uri? = null,
    val watermarkOpacityPct: Int = 25,
    val watermarkAngleDeg: Int = -35,
    val watermarkSizePct: Int = 10,
    val redactTermsText: String = "",
    val redactDpi: Int = 150,
    val redactPreview: PdfVaultEngine.RedactionPreview? = null,
    val isScanningRedact: Boolean = false,
    // Forms / Page Numbers / Metadata
    val metaTitle: String = "",
    val metaAuthor: String = "",
    val metaSubject: String = "",
    val metaKeywords: String = "",
    val pageNumPattern: String = "{n} / {total}",
    val pageNumPosition: String = "Tengah Bawah",
    val pageNumStart: Int = 1,
    val pageNumSizePct: Int = 4,
    val formValues: Map<String, String> = emptyMap(),
    // Compare A/B
    val compareUriA: Uri? = null,
    val compareUriB: Uri? = null,
    val compareNameA: String = "",
    val compareNameB: String = "",
    val comparePageIndex: Int = 0,
    val comparePreview: PdfVaultEngine.ComparisonPagePreview? = null,
    val isLoadingComparePreview: Boolean = false,
    val compareError: String? = null,
    // Job execution
    val isRunning: Boolean = false,
    val progress: ToolProgress? = null,
    val deletedOutputPaths: Set<String> = emptySet()
)

class PdfStudioViewModel(
    private val container: AppContainer,
    private val appContext: Context,
    initialTab: PdfStudioTab = PdfStudioTab.INSPECT
) : ViewModel() {

    private val _state = MutableStateFlow(
        PdfStudioUiState(
            activeTab = initialTab,
            ocrDataInstalled = PdfOcrEngine.installed(
                appContext,
                PdfOcrEngine.codeFromLabel("Indonesia")
            )
        )
    )
    val state: StateFlow<PdfStudioUiState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var inspectJob: Job? = null
    private var scanRedactJob: Job? = null
    private var comparePreviewJob: Job? = null

    fun setTab(tab: PdfStudioTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun setPreviewPage(index: Int) {
        _state.update { it.copy(previewPageIndex = index.coerceAtLeast(0)) }
    }

    fun setUnlockPassword(pwd: String) {
        _state.update { it.copy(unlockPassword = pwd) }
    }

    fun selectPrimaryPdf(uri: Uri) {
        val name = container.storage.getDisplayName(uri) ?: "document.pdf"
        _state.update {
            it.copy(
                primaryUri = uri,
                primaryName = name,
                previewPageIndex = 0,
                previewRotationDeg = 0,
                redactPreview = null,
                docError = null,
                progress = null
            )
        }
        inspectPrimary()
    }

    fun inspectPrimary() {
        val current = _state.value
        val uri = current.primaryUri ?: return
        val password = current.unlockPassword
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _state.update { it.copy(isInspecting = true, docError = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val info = PdfVaultEngine.inspectDocument(appContext, container.storage, uri, password)
                    val fullSample = if (!info.requiresPassword && info.hasExtractableText) {
                        PdfVaultEngine.extractTextSample(appContext, container.storage, uri, maxPages = 3, password = password)
                    } else {
                        ""
                    }
                    info to fullSample
                }
            }
            result.onSuccess { (info, textSample) ->
                val defaultOrder = if (info.pageCount > 0) (1..info.pageCount).toList() else emptyList()
                val formDefaults = info.formFields.associate { it.first to it.second }
                _state.update { s ->
                    s.copy(
                        isInspecting = false,
                        inspection = info,
                        extractedText = textSample,
                        selectedPages = if (info.pageCount > 0) listOf(1) else emptyList(),
                        pageOrder = defaultOrder,
                        metaTitle = info.title,
                        metaAuthor = info.author,
                        metaSubject = info.subject,
                        metaKeywords = info.keywords,
                        formValues = formDefaults,
                        compareUriA = s.compareUriA ?: uri,
                        compareNameA = if (s.compareNameA.isBlank()) s.primaryName else s.compareNameA,
                        docError = if (info.requiresPassword) {
                            if (password.isNotBlank()) {
                                appContext.getString(R.string.pdf_preview_wrong_password)
                            } else {
                                appContext.getString(R.string.pdf_preview_encrypted)
                            }
                        } else null
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val message = when (err) {
                    is InvalidPasswordException -> if (password.isNotBlank()) {
                        appContext.getString(R.string.pdf_preview_wrong_password)
                    } else {
                        appContext.getString(R.string.pdf_preview_encrypted)
                    }
                    is SecurityException -> appContext.getString(R.string.pdf_preview_encrypted)
                    else -> err.localizedMessage ?: appContext.getString(R.string.pdf_preview_error)
                }
                _state.update {
                    it.copy(
                        isInspecting = false,
                        docError = message
                    )
                }
            }
        }
    }

    // --------------------------------------------------------- Pages & Merge
    fun setSelectedPages(pages: List<Int>) {
        _state.update { it.copy(selectedPages = pages) }
    }

    fun setPageOrder(order: List<Int>) {
        _state.update { it.copy(pageOrder = order) }
    }

    fun bumpRotationPreview(delta: Int) {
        _state.update {
            val next = ((it.previewRotationDeg + delta) % 360 + 360) % 360
            it.copy(previewRotationDeg = next)
        }
    }

    fun setSplitPerFile(value: Int) {
        _state.update { it.copy(splitPerFile = value.coerceIn(1, 100)) }
    }

    fun addMergePdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newItems = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = container.storage.getDisplayName(uri) ?: "document.pdf"
                    val info = runCatching {
                        PdfVaultEngine.inspectDocument(appContext, container.storage, uri)
                    }.getOrNull()
                    PdfQueueItem(
                        uri = uri,
                        name = name,
                        pageCount = info?.pageCount ?: 0,
                        sizeBytes = info?.fileSizeBytes ?: 0L,
                        isEncrypted = info?.isEncrypted == true || info?.requiresPassword == true
                    )
                }
            }
            _state.update { it.copy(mergeQueue = it.mergeQueue + newItems) }
        }
    }

    fun moveMergeItem(index: Int, delta: Int) {
        _state.update { s ->
            val target = index + delta
            if (index !in s.mergeQueue.indices || target !in s.mergeQueue.indices) return@update s
            val mutable = s.mergeQueue.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            s.copy(mergeQueue = mutable)
        }
    }

    fun removeMergeItem(index: Int) {
        _state.update { s ->
            if (index !in s.mergeQueue.indices) return@update s
            val mutable = s.mergeQueue.toMutableList()
            mutable.removeAt(index)
            s.copy(mergeQueue = mutable)
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(imageQueue = (it.imageQueue + uris).take(40)) }
    }

    fun clearImages() {
        _state.update { it.copy(imageQueue = emptyList()) }
    }

    fun setImagePageSizeMode(mode: String) {
        _state.update { it.copy(imagePageSizeMode = mode) }
    }

    // -------------------------------------------------------- OCR & Compress
    fun setOcrLanguage(label: String) {
        val code = PdfOcrEngine.codeFromLabel(label)
        _state.update {
            it.copy(
                ocrLanguageLabel = label,
                ocrDataInstalled = PdfOcrEngine.installed(appContext, code)
            )
        }
    }

    fun setCompressDpi(dpi: Int) {
        _state.update { it.copy(compressDpi = dpi.coerceIn(72, 200)) }
    }

    fun setCompressQuality(q: Int) {
        _state.update { it.copy(compressQuality = q.coerceIn(30, 95)) }
    }

    // -------------------------------------------- Security / Watermark / Redact
    fun setEncryptUserPwd(value: String) = _state.update { it.copy(encryptUserPwd = value) }
    fun setEncryptOwnerPwd(value: String) = _state.update { it.copy(encryptOwnerPwd = value) }
    fun setEncryptKeyAlgo(value: String) = _state.update { it.copy(encryptKeyAlgo = value) }

    fun setWatermarkIsImage(isImage: Boolean) = _state.update { it.copy(watermarkIsImage = isImage) }
    fun setWatermarkText(text: String) = _state.update { it.copy(watermarkText = text) }
    fun setWatermarkImageUri(uri: Uri?) = _state.update { it.copy(watermarkImageUri = uri) }
    fun setWatermarkOpacity(pct: Int) = _state.update { it.copy(watermarkOpacityPct = pct.coerceIn(5, 100)) }
    fun setWatermarkAngle(deg: Int) = _state.update { it.copy(watermarkAngleDeg = deg.coerceIn(-90, 90)) }
    fun setWatermarkSize(pct: Int) = _state.update { it.copy(watermarkSizePct = pct.coerceIn(2, 30)) }

    fun setRedactTerms(terms: String) = _state.update { it.copy(redactTermsText = terms) }
    fun setRedactDpi(dpi: Int) = _state.update { it.copy(redactDpi = dpi.coerceIn(100, 250)) }

    fun scanRedactionMatches() {
        val s = _state.value
        val uri = s.primaryUri ?: return
        val terms = parseTerms(s.redactTermsText)
        if (terms.isEmpty()) {
            scanRedactJob?.cancel()
            _state.update { it.copy(isScanningRedact = false, redactPreview = null) }
            return
        }
        scanRedactJob?.cancel()
        scanRedactJob = viewModelScope.launch {
            _state.update { it.copy(isScanningRedact = true, docError = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PdfVaultEngine.previewRedactionHits(appContext, container.storage, uri, terms, s.unlockPassword)
                }
            }
            result.onSuccess { preview ->
                val jumpPage = preview.pagesWithHits.minOrNull()?.let { it - 1 } ?: s.previewPageIndex
                _state.update {
                    it.copy(
                        isScanningRedact = false,
                        redactPreview = preview,
                        previewPageIndex = jumpPage
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val message = when (err) {
                    is InvalidPasswordException -> appContext.getString(R.string.pdf_preview_wrong_password)
                    is SecurityException -> appContext.getString(R.string.pdf_preview_encrypted)
                    else -> err.localizedMessage ?: appContext.getString(R.string.pdf_preview_error)
                }
                _state.update {
                    it.copy(
                        isScanningRedact = false,
                        docError = message
                    )
                }
            }
        }
    }

    // --------------------------------------------------- Metadata / Forms / PageNum
    fun setMetaTitle(v: String) = _state.update { it.copy(metaTitle = v) }
    fun setMetaAuthor(v: String) = _state.update { it.copy(metaAuthor = v) }
    fun setMetaSubject(v: String) = _state.update { it.copy(metaSubject = v) }
    fun setMetaKeywords(v: String) = _state.update { it.copy(metaKeywords = v) }

    fun setPageNumPattern(v: String) = _state.update { it.copy(pageNumPattern = v) }
    fun setPageNumPosition(v: String) = _state.update { it.copy(pageNumPosition = v) }
    fun setPageNumStart(v: Int) = _state.update { it.copy(pageNumStart = v.coerceIn(1, 999)) }
    fun setPageNumSize(v: Int) = _state.update { it.copy(pageNumSizePct = v.coerceIn(2, 10)) }

    fun setFormValue(fieldName: String, value: String) {
        _state.update { it.copy(formValues = it.formValues + (fieldName to value)) }
    }

    // --------------------------------------------------------------- Compare
    fun setCompareDocA(uri: Uri) {
        val name = container.storage.getDisplayName(uri) ?: "docA.pdf"
        _state.update { it.copy(compareUriA = uri, compareNameA = name, comparePageIndex = 0, compareError = null) }
        refreshComparePagePreview(0)
    }

    fun setCompareDocB(uri: Uri) {
        val name = container.storage.getDisplayName(uri) ?: "docB.pdf"
        _state.update { it.copy(compareUriB = uri, compareNameB = name, comparePageIndex = 0, compareError = null) }
        refreshComparePagePreview(0)
    }

    fun refreshComparePagePreview(pageIndex: Int = _state.value.comparePageIndex) {
        val s = _state.value
        val uriA = s.compareUriA ?: return
        val uriB = s.compareUriB ?: return
        comparePreviewJob?.cancel()
        comparePreviewJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoadingComparePreview = true,
                    comparePageIndex = pageIndex.coerceAtLeast(0),
                    compareError = null
                )
            }
            val old = _state.value.comparePreview
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PdfVaultEngine.renderComparisonPagePreview(appContext, container.storage, uriA, uriB, pageIndex.coerceAtLeast(0))
                }
            }
            result.onSuccess { next ->
                old?.let { prev ->
                    prev.bitmapA?.takeIf { !it.isRecycled }?.recycle()
                    prev.bitmapB?.takeIf { !it.isRecycled }?.recycle()
                    prev.diffBitmap?.takeIf { !it.isRecycled }?.recycle()
                }
                _state.update {
                    it.copy(
                        isLoadingComparePreview = false,
                        comparePreview = next,
                        compareError = null
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val errMsg = when (err) {
                    is InvalidPasswordException, is SecurityException ->
                        appContext.getString(R.string.pdf_studio_compare_encrypted_error)
                    else -> err.localizedMessage ?: appContext.getString(R.string.pdf_studio_compare_failed)
                }
                _state.update {
                    it.copy(
                        isLoadingComparePreview = false,
                        compareError = errMsg
                    )
                }
            }
        }
    }

    // --------------------------------------------------------------- Execute Operations
    fun cancelOperation() {
        activeJob?.cancel()
        activeJob = null
        _state.update { it.copy(isRunning = false, progress = null) }
    }

    fun deleteOutputFile(output: ToolOutputFile) {
        runCatching { output.file.delete() }
        _state.update { it.copy(deletedOutputPaths = it.deletedOutputPaths + output.file.absolutePath) }
    }

    fun mergeQueuedPdfs(isEnglish: Boolean) {
        val uris = _state.value.mergeQueue.map { it.uri }
        runStudioJob("PDF-01", isEnglish) {
            require(uris.size >= 2) {
                if (isEnglish) "Select at least 2 PDF files" else "Pilih minimal 2 berkas PDF"
            }
            emitProgress(30, if (isEnglish) "Merging ${uris.size} documents..." else "Menggabungkan ${uris.size} dokumen...")
            val dest = container.storage.createOutputFile("PDF_MERGE", "pdf")
            val pages = PdfVaultEngine.merge(appContext, container.storage, uris, dest)
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$pages pages merged ($sizeLabel)"
                } else {
                    "$pages halaman digabung ($sizeLabel)"
                }
            )
        }
    }

    fun splitPrimaryPdf(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-02", isEnglish) {
            emitProgress(35, if (isEnglish) "Splitting pages..." else "Memisahkan halaman...")
            val dest = container.storage.createOutputFile("PDF_SPLIT", "zip")
            val (_, parts) = PdfVaultEngine.split(appContext, container.storage, uri, dest, s.splitPerFile, s.unlockPassword)
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                summary = if (isEnglish) {
                    "$parts PDF files in ZIP ($sizeLabel)"
                } else {
                    "$parts berkas PDF di ZIP ($sizeLabel)"
                }
            )
        }
    }

    fun rotatePages(angle: Int, applySelectedOnly: Boolean, isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        val targets = if (applySelectedOnly) s.selectedPages else emptyList()
        runStudioJob("PDF-03", isEnglish) {
            emitProgress(40, if (isEnglish) "Rotating pages..." else "Memutar halaman...")
            val dest = container.storage.createOutputFile("PDF_ROTATE", "pdf")
            val count = PdfVaultEngine.rotate(appContext, container.storage, uri, dest, angle, targets, password = s.unlockPassword)
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$count pages rotated ${angle}° • $sizeLabel"
                } else {
                    "$count halaman diputar ${angle}° • $sizeLabel"
                }
            )
        }
    }

    fun applyReorderPages(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-04", isEnglish) {
            require(s.pageOrder.isNotEmpty()) {
                if (isEnglish) "Page order is empty" else "Urutan halaman masih kosong"
            }
            emitProgress(40, if (isEnglish) "Reordering pages..." else "Menyusun ulang halaman...")
            val dest = container.storage.createOutputFile("PDF_REORDER", "pdf")
            val count = PdfVaultEngine.reorder(appContext, container.storage, uri, dest, s.pageOrder, password = s.unlockPassword)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "New order: ${s.pageOrder.joinToString(",")} ($count pages)"
                } else {
                    "Urutan baru: ${s.pageOrder.joinToString(",")} ($count halaman)"
                }
            )
        }
    }

    fun extractSelectedPages(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-05", isEnglish) {
            require(s.selectedPages.isNotEmpty()) {
                if (isEnglish) "Select at least 1 page first" else "Pilih minimal 1 halaman dahulu"
            }
            emitProgress(40, if (isEnglish) "Extracting ${s.selectedPages.size} pages..." else "Mengekstrak ${s.selectedPages.size} halaman...")
            val dest = container.storage.createOutputFile("PDF_EXTRACT", "pdf")
            val count = PdfVaultEngine.extract(appContext, container.storage, uri, dest, s.selectedPages, password = s.unlockPassword)
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$count pages extracted • $sizeLabel"
                } else {
                    "$count halaman diekstrak • $sizeLabel"
                }
            )
        }
    }

    fun createPdfFromImages(isEnglish: Boolean) {
        val s = _state.value
        runStudioJob("PDF-08", isEnglish) {
            require(s.imageQueue.isNotEmpty()) {
                if (isEnglish) "Pick at least 1 image" else "Pilih minimal 1 gambar"
            }
            emitProgress(40, if (isEnglish) "Building PDF from ${s.imageQueue.size} images..." else "Menyusun PDF dari ${s.imageQueue.size} gambar...")
            val dest = container.storage.createOutputFile("IMG_TO_PDF", "pdf")
            val count = PdfVaultEngine.imagesToPdf(appContext, s.imageQueue, dest, pageSizeMode = s.imagePageSizeMode)
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$count PDF pages • $sizeLabel"
                } else {
                    "$count halaman PDF • $sizeLabel"
                }
            )
        }
    }

    fun compressPrimaryPdf(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-09", isEnglish) {
            emitProgress(35, if (isEnglish) "Rasterizing pages..." else "Merasterisasi halaman...")
            val dest = container.storage.createOutputFile("PDF_COMPRESS", "pdf")
            val report = PdfVaultEngine.compress(
                appContext, container.storage, uri, dest,
                s.compressDpi, s.compressQuality, password = s.unlockPassword
            )
            val savedPct = if (report.beforeBytes > 0) {
                ((report.beforeBytes - report.afterBytes) * 100.0 / report.beforeBytes).toInt()
            } else 0
            val beforeLabel = container.storage.formatBytes(report.beforeBytes)
            val afterLabel = container.storage.formatBytes(report.afterBytes)
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "${report.pages} pages • $beforeLabel → $afterLabel ($savedPct%)"
                } else {
                    "${report.pages} hal • $beforeLabel → $afterLabel ($savedPct%)"
                }
            )
        }
    }

    fun encryptPrimaryPdf(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-10", isEnglish) {
            require(s.encryptUserPwd.isNotBlank() || s.encryptOwnerPwd.isNotBlank()) {
                if (isEnglish) "Provide at least one password" else "Isi minimal satu kata sandi"
            }
            val keyBits = when (s.encryptKeyAlgo) {
                "AES-128" -> 128
                "RC4-128" -> 128
                else -> 256
            }
            val preferAes = s.encryptKeyAlgo != "RC4-128"
            emitProgress(40, if (isEnglish) "Encrypting PDF..." else "Mengenkripsi PDF...")
            val dest = container.storage.createOutputFile("PDF_ENCRYPT", "pdf")
            val pages = PdfVaultEngine.encrypt(
                appContext, container.storage, uri, dest,
                s.encryptUserPwd, s.encryptOwnerPwd, keyBits, preferAes,
                password = s.unlockPassword
            )
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$pages pages encrypted (${s.encryptKeyAlgo}) • $sizeLabel"
                } else {
                    "$pages hal terenkripsi ${s.encryptKeyAlgo} • $sizeLabel"
                }
            )
        }
    }

    fun decryptPrimaryPdf(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-11", isEnglish) {
            emitProgress(40, if (isEnglish) "Decrypting PDF..." else "Membuka enkripsi PDF...")
            val dest = container.storage.createOutputFile("PDF_DECRYPT", "pdf")
            val pwd = s.unlockPassword.ifBlank { s.encryptUserPwd }
            val pages = PdfVaultEngine.decrypt(appContext, container.storage, uri, dest, pwd)
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$pages pages decrypted • $sizeLabel"
                } else {
                    "$pages hal bebas enkripsi • $sizeLabel"
                }
            )
        }
    }

    fun applyWatermark(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-12", isEnglish) {
            val dest = container.storage.createOutputFile("PDF_WATERMARK", "pdf")
            val pages = if (s.watermarkIsImage) {
                val img = s.watermarkImageUri ?: error(
                    if (isEnglish) "Pick a watermark image first" else "Pilih gambar watermark dahulu"
                )
                emitProgress(45, if (isEnglish) "Applying image watermark..." else "Menempelkan watermark gambar...")
                PdfVaultEngine.addImageWatermark(
                    appContext, container.storage, uri, img, dest,
                    s.watermarkOpacityPct, s.watermarkSizePct.coerceAtLeast(10),
                    password = s.unlockPassword
                )
            } else {
                require(s.watermarkText.isNotBlank()) {
                    if (isEnglish) "Watermark text cannot be blank" else "Teks watermark masih kosong"
                }
                emitProgress(45, if (isEnglish) "Applying text watermark..." else "Menempelkan watermark teks...")
                PdfVaultEngine.addTextWatermark(
                    appContext, container.storage, uri, dest,
                    s.watermarkText, s.watermarkOpacityPct, s.watermarkAngleDeg, s.watermarkSizePct,
                    password = s.unlockPassword
                )
            }
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "Watermark applied to $pages pages • $sizeLabel"
                } else {
                    "Watermark pada $pages halaman • $sizeLabel"
                }
            )
        }
    }

    fun removeObjectWatermark(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-13", isEnglish) {
            emitProgress(45, if (isEnglish) "Scanning annotations & layers..." else "Memindai anotasi & layer...")
            val dest = container.storage.createOutputFile("PDF_UNWATERMARK", "pdf")
            val removed = PdfVaultEngine.removeWatermarkObjects(
                appContext, container.storage, uri, dest, password = s.unlockPassword
            )
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (removed == 0) {
                    if (isEnglish) {
                        "0 removable annotation/OCG objects found (burned-in content watermarks remain)."
                    } else {
                        "0 objek anotasi/OCG terhapus (watermark menyatu tidak bisa dihapus otomatis)."
                    }
                } else {
                    if (isEnglish) {
                        "$removed watermark objects removed • $sizeLabel"
                    } else {
                        "$removed objek watermark dihapus • $sizeLabel"
                    }
                }
            )
        }
    }

    fun runPermanentRedaction(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        val terms = parseTerms(s.redactTermsText)
        runStudioJob("PDF-14", isEnglish) {
            require(terms.isNotEmpty()) {
                if (isEnglish) "Enter at least one term to redact" else "Isi minimal satu istilah yang ingin dihapus"
            }
            emitProgress(35, if (isEnglish) "Locating terms & flattening pages..." else "Mencari istilah & meratakan halaman...")
            val dest = container.storage.createOutputFile("PDF_REDACT", "pdf")
            val report = PdfVaultEngine.redact(
                appContext, container.storage, uri, dest, terms, s.redactDpi, s.unlockPassword
            )
            val verifyBadge = when {
                report.boxes == 0 -> report.note
                report.verified -> if (isEnglish) "Text verification PASSED." else "Verifikasi ekstraksi teks LULUS."
                else -> if (isEnglish) "Text verification FAILED." else "Verifikasi ekstraksi teks GAGAL."
            }
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "${report.boxes} regions redacted across ${report.pages} pages • $verifyBadge"
                } else {
                    "${report.boxes} area ditutup pada ${report.pages} hal • $verifyBadge"
                }
            )
        }
    }

    fun runOcrExtract(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-15", isEnglish) {
            val code = PdfOcrEngine.codeFromLabel(s.ocrLanguageLabel)
            val missing = PdfOcrEngine.missing(appContext, listOf(code))
            if (missing.isNotEmpty()) {
                emitProgress(20, if (isEnglish) "Downloading OCR data: $code..." else "Mengunduh data OCR: $code...")
                PdfOcrEngine.ensureTrainedData(appContext, listOf(code), isEnglish = isEnglish) { pct, msg ->
                    emitProgress(20 + pct / 4, msg)
                }
                _state.update { it.copy(ocrDataInstalled = true) }
            }
            val total = PdfRenderUtils.pageCount(appContext, container.storage, uri, password = s.unlockPassword)
            require(total > 0) { if (isEnglish) "PDF has no pages" else "PDF tidak punya halaman" }
            val builder = StringBuilder()
            emitProgress(55, if (isEnglish) "OCR $total pages ($code)..." else "OCR $total halaman ($code)...")
            PdfRenderUtils.renderPages(
                appContext, container.storage, uri, dpi = 200, password = s.unlockPassword
            ) { idx, rendered, _ ->
                val text = try {
                    PdfOcrEngine.recognize(appContext, rendered.bitmap, listOf(code), isEnglish = isEnglish)
                } finally {
                    if (!rendered.bitmap.isRecycled) rendered.bitmap.recycle()
                }
                val pageHeader = if (isEnglish) "=== Page ${idx + 1} ===" else "=== Halaman ${idx + 1} ==="
                builder.append(pageHeader).append("\n").append(text).append("\n\n")
                true
            }
            val dest = container.storage.createOutputFile("PDF_OCR_TEXT", "txt")
            val fullText = builder.toString().trim()
            dest.writeText(fullText)
            _state.update { it.copy(extractedText = fullText.take(6000)) }
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "text/plain", dest.name)),
                textOutput = fullText.take(4000),
                summary = if (isEnglish) {
                    "OCR $code ($total pages) • $sizeLabel"
                } else {
                    "OCR $code ($total hal) • $sizeLabel"
                }
            )
        }
    }

    fun runAiStructureAnalysis(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-17", isEnglish) {
            val tool = ToolRegistry.findById("PDF-17") ?: error("PDF-17 tidak terdaftar")
            val ctx = ToolContext(
                appContext = appContext,
                storage = container.storage,
                isEnglish = isEnglish,
                ai = container.aiClient,
                models = container.modelManager
            )
            var terminal: ToolResult? = null
            tool.run(
                ctx,
                ToolInput(
                    uris = listOf(uri),
                    params = mapOf("maxPages" to 2f, "password" to s.unlockPassword)
                )
            ).collect { p ->
                when (p) {
                    is ToolProgress.Running -> emitProgress(p.percent, p.message)
                    is ToolProgress.Done -> terminal = p.result
                    is ToolProgress.Failed -> error(p.error)
                    ToolProgress.Queued -> Unit
                }
            }
            terminal ?: error("AI analysis did not return a result")
        }
    }

    fun saveMetadata(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-19", isEnglish) {
            val values = mapOf(
                "title" to s.metaTitle,
                "author" to s.metaAuthor,
                "subject" to s.metaSubject,
                "keywords" to s.metaKeywords
            )
            emitProgress(45, if (isEnglish) "Writing metadata..." else "Menyimpan metadata...")
            val dest = container.storage.createOutputFile("PDF_METADATA", "pdf")
            val pages = PdfVaultEngine.writeMetadata(
                appContext, container.storage, uri, dest, values, password = s.unlockPassword
            )
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "Metadata saved across $pages pages • $sizeLabel"
                } else {
                    "Metadata diperbarui pada $pages halaman • $sizeLabel"
                }
            )
        }
    }

    fun applyPageNumbers(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-18", isEnglish) {
            emitProgress(45, if (isEnglish) "Adding page numbers..." else "Menambahkan nomor halaman...")
            val dest = container.storage.createOutputFile("PDF_NUMBERS", "pdf")
            val pages = PdfVaultEngine.addPageNumbers(
                appContext, container.storage, uri, dest,
                s.pageNumPattern.ifBlank { "{n}" },
                s.pageNumStart,
                s.pageNumPosition,
                s.pageNumSizePct,
                password = s.unlockPassword
            )
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "Page numbers added to $pages pages"
                } else {
                    "Nomor halaman ditambahkan ke $pages halaman"
                }
            )
        }
    }

    fun fillAcroForm(isEnglish: Boolean) {
        val s = _state.value
        val uri = s.primaryUri ?: return
        runStudioJob("PDF-21", isEnglish) {
            require(s.formValues.isNotEmpty()) {
                if (isEnglish) "No AcroForm fields available" else "Tidak ada field AcroForm untuk diisi"
            }
            emitProgress(45, if (isEnglish) "Filling AcroForm fields..." else "Mengisi field form...")
            val dest = container.storage.createOutputFile("PDF_FORM", "pdf")
            val filled = PdfVaultEngine.fillForm(
                appContext, container.storage, uri, dest, s.formValues, password = s.unlockPassword
            )
            val sizeLabel = container.storage.formatBytes(dest.length())
            ToolResult(
                outputs = listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                summary = if (isEnglish) {
                    "$filled AcroForm fields saved • $sizeLabel"
                } else {
                    "$filled field AcroForm tersimpan • $sizeLabel"
                }
            )
        }
    }

    fun runFullComparison(isEnglish: Boolean) {
        val s = _state.value
        val uriA = s.compareUriA
        val uriB = s.compareUriB
        runStudioJob("PDF-20", isEnglish) {
            require(uriA != null && uriB != null) {
                if (isEnglish) "Select both Document A and Document B" else "Pilih Dokumen A dan Dokumen B"
            }
            emitProgress(35, if (isEnglish) "Comparing text & page visuals..." else "Membandingkan teks & tampilan halaman...")
            val zip = container.storage.createOutputFile("PDF_DIFF_IMG", "zip")
            val report = PdfVaultEngine.compare(appContext, container.storage, uriA, uriB, zip)
            val txt = container.storage.createOutputFile("PDF_DIFF_REPORT", "txt")
            val body = if (isEnglish) {
                buildString {
                    appendLine("Document Comparison")
                    appendLine("A: ${s.compareNameA} (${report.pagesA} pages)")
                    appendLine("B: ${s.compareNameB} (${report.pagesB} pages)")
                    appendLine("Identical pages: ${report.identicalPages}")
                    appendLine("Different pages: ${report.differentPages}")
                    appendLine("Unique lines only in A: ${report.removedLines}")
                    appendLine("Unique lines only in B: ${report.addedLines}")
                    appendLine("Method: unique-line set diff; 8 px grid visual comparison.")
                }
            } else {
                buildString {
                    appendLine("Perbandingan dokumen")
                    appendLine("A: ${s.compareNameA} (${report.pagesA} halaman)")
                    appendLine("B: ${s.compareNameB} (${report.pagesB} halaman)")
                    appendLine("Halaman identik: ${report.identicalPages}")
                    appendLine("Halaman berbeda: ${report.differentPages}")
                    appendLine("Baris unik hanya di A: ${report.removedLines}")
                    appendLine("Baris unik hanya di B: ${report.addedLines}")
                    appendLine("Metode: diff himpunan baris unik; diff visual pada kisi 8 px.")
                }
            }
            txt.writeText(body)
            ToolResult(
                outputs = listOf(
                    ToolOutputFile(txt, "text/plain", txt.name),
                    ToolOutputFile(zip, "application/zip", zip.name)
                ),
                textOutput = body,
                summary = if (isEnglish) {
                    "${report.differentPages} different, ${report.identicalPages} identical pages • +${report.addedLines}/-${report.removedLines} unique lines"
                } else {
                    "${report.differentPages} hal berbeda, ${report.identicalPages} identik • +${report.addedLines}/-${report.removedLines} baris unik"
                }
            )
        }
    }

    private fun parseTerms(raw: String): List<String> =
        raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun emitProgress(percent: Int, message: String) {
        _state.update { it.copy(progress = ToolProgress.Running(percent.coerceIn(1, 99), message)) }
    }

    private fun runStudioJob(
        toolId: String,
        isEnglish: Boolean,
        block: suspend () -> ToolResult
    ) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            if (!container.storage.hasEnoughDiskSpace()) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        progress = ToolProgress.Failed(appContext.getString(R.string.job_error_low_disk))
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isRunning = true,
                    deletedOutputPaths = emptySet(),
                    progress = ToolProgress.Running(10, appContext.getString(R.string.progress_queued))
                )
            }
            try {
                val result = withContext(Dispatchers.IO) { block() }
                val firstOut = result.outputs.firstOrNull()
                val title = ToolRegistry.findById(toolId)?.title(isEnglish) ?: toolId
                container.workspace.recordHistory(
                    toolId = toolId,
                    toolTitle = title,
                    groupName = ToolGroup.PDF.name,
                    outputFile = firstOut?.file,
                    mimeType = firstOut?.mimeType ?: "text/plain",
                    summary = result.summary,
                    success = true
                )
                _state.update { it.copy(isRunning = false, progress = ToolProgress.Done(result)) }
            } catch (ce: CancellationException) {
                _state.update { it.copy(isRunning = false, progress = null) }
                throw ce
            } catch (_: InvalidPasswordException) {
                val msg = if (_state.value.unlockPassword.isNotBlank()) {
                    appContext.getString(R.string.pdf_preview_wrong_password)
                } else {
                    appContext.getString(R.string.pdf_preview_encrypted)
                }
                _state.update { it.copy(isRunning = false, progress = ToolProgress.Failed(msg)) }
            } catch (_: SecurityException) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        progress = ToolProgress.Failed(appContext.getString(R.string.pdf_preview_encrypted))
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        progress = ToolProgress.Failed(t.localizedMessage ?: "PDF operation failed")
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _state.value.comparePreview?.let { prev ->
            prev.bitmapA?.takeIf { !it.isRecycled }?.recycle()
            prev.bitmapB?.takeIf { !it.isRecycled }?.recycle()
            prev.diffBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    class Factory(
        private val container: AppContainer,
        private val context: Context,
        private val initialTab: PdfStudioTab = PdfStudioTab.INSPECT
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PdfStudioViewModel(container, context.applicationContext, initialTab) as T
    }
}
