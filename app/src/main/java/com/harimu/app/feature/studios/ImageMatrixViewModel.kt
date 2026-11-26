package com.harimu.app.feature.studios

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.ai.BackgroundRemovalRequest
import com.harimu.app.core.ai.BackgroundSegmenters
import com.harimu.app.core.ai.SegmentationMode
import com.harimu.app.core.engines.ImageFormatMatrix
import com.harimu.app.core.engines.ImageIO
import com.harimu.app.core.engines.ImageLayerCompositor
import com.harimu.app.core.engines.LayerHistory
import com.harimu.app.core.engines.MatrixLayer
import com.harimu.app.core.engines.MatrixLayerKind
import com.harimu.app.core.engines.MatrixLayerMath
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ImageMatrixUiState(
    val layers: List<MatrixLayer> = emptyList(),
    val selectedLayerId: String? = null,
    val canvasPresetIndex: Int = 0,
    val backgroundHex: String = "#11141C",
    val preview: Bitmap? = null,
    val isRendering: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: ToolProgress? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastMessage: String? = null
) {
    val selectedLayer: MatrixLayer?
        get() = layers.firstOrNull { it.id == selectedLayerId } ?: layers.lastOrNull()

    val canvasSize: Pair<Int, Int>
        get() = ImageLayerCompositor.canvasPresets()
            .getOrElse(canvasPresetIndex) { ImageLayerCompositor.canvasPresets().first() }
            .second
}

/**
 * Image Matrix Studio (IMG-47) — multilayer canvas with image/text/sticker layers, transform,
 * ordering, blend modes, undo/redo, AI/basic background removal, and full-res export.
 * Preview and export both go through [ImageLayerCompositor] so the composition is identical.
 */
class ImageMatrixViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ImageMatrixUiState())
    val state: StateFlow<ImageMatrixUiState> = _state.asStateFlow()

    private val history = LayerHistory()
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private var renderJob: Job? = null

    val canvasPresets: List<Pair<String, Pair<Int, Int>>> get() = ImageLayerCompositor.canvasPresets()
    val blendModes: List<String> get() = ImageLayerCompositor.layerBlendLabels()

    fun initialize() {
        if (_state.value.preview == null && renderJob == null) scheduleRender(0L)
    }

    // ------------------------------------------------------------- layers

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val key = uri.toString()
            if (bitmaps.size >= MAX_CACHED_BITMAPS && !bitmaps.containsKey(key)) {
                _state.update { it.copy(lastMessage = "Batas $MAX_CACHED_BITMAPS gambar tercapai; hapus layer lama dulu") }
                return@launch
            }
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ImageIO.decodePreview(appContext, uri, 2048) }.getOrNull()
            }
            if (bitmap == null) {
                _state.update { it.copy(lastMessage = "Gambar tidak dapat dibaca") }
                return@launch
            }
            bitmaps[key] = bitmap
            val layer = ImageLayerCompositor.defaultImageLayer(UUID.randomUUID().toString(), key)
            pushLayers(history.current + layer)
            _state.update { it.copy(selectedLayerId = layer.id) }
        }
    }

    fun addText(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(lastMessage = "Tulis teks layer terlebih dahulu") }
            return
        }
        val layer = ImageLayerCompositor.defaultTextLayer(UUID.randomUUID().toString(), text.trim())
        pushLayers(history.current + layer)
        _state.update { it.copy(selectedLayerId = layer.id) }
    }

    fun addSticker(emoji: String) {
        val value = emoji.trim().ifBlank { "⭐" }
        val layer = ImageLayerCompositor.defaultStickerLayer(UUID.randomUUID().toString(), value)
        pushLayers(history.current + layer)
        _state.update { it.copy(selectedLayerId = layer.id) }
    }

    fun selectLayer(id: String?) {
        _state.update { it.copy(selectedLayerId = id) }
    }

    fun updateLayer(id: String, block: (MatrixLayer) -> MatrixLayer) {
        val next = history.current.map { if (it.id == id) block(it) else it }
        pushLayers(next)
    }

    /** Live edit while dragging a slider or moving on canvas: re-renders without creating undo step yet. */
    fun previewLayer(id: String, block: (MatrixLayer) -> MatrixLayer) {
        val next = _state.value.layers.map { if (it.id == id) block(it) else it }
        _state.update { it.copy(layers = next) }
        scheduleRender()
    }

    /** Nudges or drags a layer directly using [MatrixLayerMath.moved]. */
    fun moveSelectedBy(dxPct: Float, dyPct: Float) {
        val selected = _state.value.selectedLayer ?: return
        previewLayer(selected.id) { layer -> MatrixLayerMath.moved(layer, dxPct, dyPct) }
    }

    fun centerLayer(id: String) {
        updateLayer(id) { it.copy(xPct = 50f, yPct = 50f) }
    }

    fun resetTransform(id: String) {
        updateLayer(id) {
            it.copy(
                xPct = 50f,
                yPct = 50f,
                scalePct = 100f,
                rotationDeg = 0f,
                opacityPct = 100,
                blend = "Normal"
            )
        }
    }

    fun clearCanvas() {
        if (history.current.isEmpty()) return
        pushLayers(emptyList())
        _state.update { it.copy(selectedLayerId = null) }
    }

    /** Records the current (possibly previewed) layer list as one undo step. */
    fun commitLayers() {
        val current = _state.value.layers
        if (current == history.current) return
        history.push(current)
        _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    /**
     * IMG-12 di dalam Image Matrix: menghapus latar layer gambar terpilih melalui
     * [BackgroundSegmenters.default] (default Penuh ML Kit, otomatis fallback ke Dasar bila gagal).
     * Hasil disimpan di cache dengan kunci baru agar undo/redo tetap bisa kembali ke gambar asli.
     */
    fun removeBackgroundOfLayer(id: String, mode: SegmentationMode = SegmentationMode.FULL) {
        val layer = history.current.firstOrNull { it.id == id } ?: return
        if (layer.kind != MatrixLayerKind.IMAGE) return
        val source = bitmaps[layer.uri] ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRendering = true) }
            val outcome = runCatching {
                BackgroundSegmenters.default.removeBackground(
                    context = appContext,
                    source = source,
                    request = BackgroundRemovalRequest(mode = mode)
                )
            }.getOrNull()
            _state.update { it.copy(isRendering = false) }
            if (outcome == null) {
                _state.update { it.copy(lastMessage = "Hapus latar gagal (gambar terlalu besar?)") }
                return@launch
            }
            val key = "${layer.uri}#nobg-${System.currentTimeMillis()}"
            bitmaps[key] = outcome.bitmap
            updateLayer(id) { it.copy(uri = key) }
            _state.update { it.copy(lastMessage = "Latar layer dihapus (${outcome.engineSummary(false)})") }
        }
    }

    fun updateText(id: String, text: String) {
        previewLayer(id) { it.copy(text = text) }
        commitLayers()
    }

    fun setLayerColor(id: String, hex: String) {
        previewLayer(id) { it.copy(colorHex = hex) }
        commitLayers()
    }

    fun deleteLayer(id: String) {
        val next = history.current.filterNot { it.id == id }
        if (next.size == history.current.size) return
        pushLayers(next)
        releaseUnreferencedBitmaps(next)
        if (_state.value.selectedLayerId == id) {
            _state.update { it.copy(selectedLayerId = next.lastOrNull()?.id) }
        }
    }

    fun duplicateLayer(id: String) {
        val source = history.current.firstOrNull { it.id == id } ?: return
        val copy = source.copy(id = UUID.randomUUID().toString(), yPct = (source.yPct + 4f).coerceAtMost(100f))
        pushLayers(history.current + copy)
        _state.update { it.copy(selectedLayerId = copy.id) }
    }

    /** delta = -1 sends the layer one step down the stack, +1 brings it forward. */
    fun moveLayer(id: String, delta: Int) {
        val layers = history.current.toMutableList()
        val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, layers.lastIndex)
        if (target == index) return
        val layer = layers.removeAt(index)
        layers.add(target, layer)
        pushLayers(layers)
    }

    fun toggleVisibility(id: String) {
        updateLayer(id) { it.copy(visible = !it.visible) }
    }

    fun undo() {
        val previous = history.undo() ?: return
        applyHistory(previous)
    }

    fun redo() {
        val next = history.redo() ?: return
        applyHistory(next)
    }

    private fun applyHistory(layers: List<MatrixLayer>) {
        val currentSelection = _state.value.selectedLayerId
        _state.update {
            it.copy(
                layers = layers,
                selectedLayerId = layers.firstOrNull { layer -> layer.id == currentSelection }?.id,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        scheduleRender()
    }

    private fun pushLayers(next: List<MatrixLayer>) {
        history.push(next)
        _state.update {
            it.copy(layers = next, canUndo = history.canUndo, canRedo = history.canRedo)
        }
        scheduleRender()
    }

    // ------------------------------------------------------------ canvas

    fun setCanvasPreset(index: Int) {
        _state.update { it.copy(canvasPresetIndex = index.coerceIn(0, canvasPresets.lastIndex)) }
        scheduleRender(0L)
    }

    fun setBackground(hex: String) {
        _state.update { it.copy(backgroundHex = hex) }
        scheduleRender()
    }

    fun consumeMessage() {
        _state.update { it.copy(lastMessage = null) }
    }

    fun resetExportProgress() {
        _state.update { it.copy(exportProgress = null) }
    }

    // ------------------------------------------------------------ render

    private fun scheduleRender(debounceMs: Long = 90L) {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val snapshot = _state.value
            _state.update { it.copy(isRendering = true) }
            val (canvasW, canvasH) = snapshot.canvasSize
            val (previewW, previewH) = ImageLayerCompositor.scaledCanvas(canvasW, canvasH, 720)
            val bitmap = withContext(Dispatchers.Default) {
                ImageLayerCompositor.compose(
                    layers = snapshot.layers,
                    canvasWidth = previewW,
                    canvasHeight = previewH,
                    backgroundHex = snapshot.backgroundHex,
                    bitmapFor = { layer -> bitmaps[layer.uri] }
                )
            }
            _state.update { it.copy(preview = bitmap, isRendering = false) }
        }
    }

    // ------------------------------------------------------------ export

    fun export(onDone: (File?, String) -> Unit) {
        exportComposition(format = "PNG", quality = 100, trackExportDialog = false, onDone = onDone)
    }

    fun exportComposition(
        format: String = "PNG",
        quality: Int = 95,
        trackExportDialog: Boolean = true,
        onDone: (File?, String) -> Unit = { _, _ -> }
    ) {
        val snapshot = _state.value
        if (snapshot.layers.isEmpty()) {
            val msg = "Tambahkan minimal 1 layer sebelum ekspor"
            if (trackExportDialog) _state.update { it.copy(exportProgress = ToolProgress.Failed(msg)) }
            onDone(null, msg)
            return
        }
        if (snapshot.isExporting) return

        _state.update {
            it.copy(
                isExporting = true,
                exportProgress = if (trackExportDialog) ToolProgress.Running(20, "Merender komposisi...") else null
            )
        }

        viewModelScope.launch {
            val entry = runCatching { ImageFormatMatrix.requireEncodable(format) }
                .getOrElse { ImageFormatMatrix.requireEncodable("PNG") }
            val (canvasW, canvasH) = snapshot.canvasSize
            val result = runCatching {
                val bitmap = withContext(Dispatchers.Default) {
                    ImageLayerCompositor.compose(
                        layers = snapshot.layers,
                        canvasWidth = canvasW,
                        canvasHeight = canvasH,
                        backgroundHex = snapshot.backgroundHex,
                        bitmapFor = { layer -> bitmaps[layer.uri] }
                    )
                }
                if (trackExportDialog) {
                    _state.update { it.copy(exportProgress = ToolProgress.Running(75, "Menyimpan ${entry.id}...")) }
                }
                val dest = container.storage.createOutputFile("MATRIX", ImageFormatMatrix.extensionFor(entry.id))
                val mime = ImageFormatMatrix.mimeTypeFor(entry.id)
                withContext(Dispatchers.IO) { ImageIO.encode(bitmap, dest, entry.id, quality) }
                bitmap.recycle()
                val summary = "Komposisi ${snapshot.layers.size} layer ${canvasW}×${canvasH} (${entry.id} • ${container.storage.formatBytes(dest.length())})"
                container.workspace.recordHistory(
                    toolId = "IMG-47",
                    toolTitle = "Image Matrix Studio",
                    groupName = "IMAGE",
                    outputFile = dest,
                    mimeType = mime,
                    summary = summary
                )
                Triple(dest, mime, summary)
            }
            _state.update { it.copy(isExporting = false) }
            result.fold(
                onSuccess = { (file, mime, summary) ->
                    val outputFile = ToolOutputFile(file, mime, file.name)
                    if (trackExportDialog) {
                        _state.update {
                            it.copy(
                                exportProgress = ToolProgress.Done(
                                    ToolResult(outputs = listOf(outputFile), summary = summary)
                                )
                            )
                        }
                    }
                    onDone(file, "Diekspor ${file.name} (${container.storage.formatBytes(file.length())})")
                },
                onFailure = { error ->
                    val message = error.localizedMessage ?: "Ekspor gagal"
                    if (trackExportDialog) {
                        _state.update { it.copy(exportProgress = ToolProgress.Failed(message)) }
                    }
                    onDone(null, message)
                }
            )
        }
    }

    /** Lepas bitmap yang tidak lagi dirujuk layer mana pun (setelah hapus/undo/redo). */
    private fun releaseUnreferencedBitmaps(layers: List<MatrixLayer>) {
        val referenced = layers.map { it.uri }.toSet()
        val stale = bitmaps.keys.filterNot { it in referenced }
        stale.forEach { key ->
            bitmaps.remove(key)?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    override fun onCleared() {
        renderJob?.cancel()
        bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmaps.clear()
        _state.value.preview?.takeIf { !it.isRecycled }?.recycle()
        super.onCleared()
    }

    fun layerKindLabel(kind: MatrixLayerKind, isEnglish: Boolean = false): String = when (kind) {
        MatrixLayerKind.IMAGE -> if (isEnglish) "Image" else "Gambar"
        MatrixLayerKind.TEXT -> if (isEnglish) "Text" else "Teks"
        MatrixLayerKind.STICKER -> if (isEnglish) "Sticker" else "Stiker"
    }

    private companion object {
        const val MAX_CACHED_BITMAPS = 24
    }

    class Factory(
        private val container: AppContainer,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ImageMatrixViewModel(container, appContext) as T
    }
}
