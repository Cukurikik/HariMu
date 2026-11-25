package com.harimu.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Mode pemilihan engine untuk penghapus latar (IMG-12, IMG-43, IMG-47). */
enum class SegmentationMode(val labelId: String, val labelEn: String) {
    FULL("Penuh (ML Kit)", "Full (ML Kit)"),
    BASIC("Dasar (Heuristik)", "Basic (Heuristic)");

    companion object {
        fun fromParam(raw: String): SegmentationMode {
            val normalized = raw.trim().lowercase()
            return if (normalized.startsWith("dasar") || normalized.startsWith("basic")) {
                BASIC
            } else {
                FULL
            }
        }
    }
}

enum class SegmentationEngineUsed(val labelId: String, val labelEn: String) {
    MLKIT_SUBJECT("Penuh (ML Kit Subject Segmentation)", "Full (ML Kit Subject Segmentation)"),
    BASIC_FLOOD_FILL("Dasar (flood fill tepi)", "Basic (edge flood fill)")
}

data class BackgroundRemovalRequest(
    val mode: SegmentationMode = SegmentationMode.FULL,
    val tolerancePercent: Int = 28,
    val featherPx: Int = 2,
    val backgroundColorHex: String = ""
)

data class BackgroundRemovalResult(
    val bitmap: Bitmap,
    val engineUsed: SegmentationEngineUsed,
    val fallbackFromFull: Boolean = false,
    val fallbackReason: String? = null
) {
    fun engineSummary(isEnglish: Boolean): String = when {
        fallbackFromFull -> {
            val reason = fallbackReason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            if (isEnglish) {
                "Fallback to Basic (flood fill) after ML Kit failed$reason"
            } else {
                "Fallback otomatis ke Dasar (flood fill) karena ML Kit gagal$reason"
            }
        }
        isEnglish -> engineUsed.labelEn
        else -> engineUsed.labelId
    }
}

/**
 * Kontrak tunggal segmentasi latar belakang untuk IMG-12, IMG-43, dan IMG-47.
 * Implementasi tidak mendaur ulang [source]; pemanggil bertanggung jawab atas siklus hidup [source].
 */
interface BackgroundSegmenter {
    val engineKind: SegmentationEngineUsed
    suspend fun segment(context: Context, source: Bitmap, request: BackgroundRemovalRequest): Bitmap
}

/** Implementasi Dasar: flood fill dari keempat tepi kanvas ([AiLocalEngines.removeBackground]). */
object BasicFloodFillSegmenter : BackgroundSegmenter {
    override val engineKind: SegmentationEngineUsed = SegmentationEngineUsed.BASIC_FLOOD_FILL

    override suspend fun segment(
        context: Context,
        source: Bitmap,
        request: BackgroundRemovalRequest
    ): Bitmap = withContext(Dispatchers.Default) {
        AiLocalEngines.removeBackground(
            source = source,
            tolerancePercent = request.tolerancePercent,
            featherPx = request.featherPx,
            backgroundColorHex = request.backgroundColorHex
        )
    }
}

/**
 * Implementasi Penuh: Google ML Kit Subject Segmentation melalui Play Services.
 * Menggunakan mask confidence foreground agar pelembutan tepi dan latar warna konsisten.
 */
object MlKitSubjectSegmenter : BackgroundSegmenter {
    override val engineKind: SegmentationEngineUsed = SegmentationEngineUsed.MLKIT_SUBJECT

    override suspend fun segment(
        context: Context,
        source: Bitmap,
        request: BackgroundRemovalRequest
    ): Bitmap {
        val width = source.width
        val height = source.height
        require(width.toLong() * height <= AiLocalEngines.MAX_BLUR_PIXELS) {
            "Gambar terlalu besar untuk segmentasi lokal (maks ${AiLocalEngines.MAX_BLUR_PIXELS / 1_000_000} MP)."
        }
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val client = SubjectSegmentation.getClient(options)
        return try {
            ensureModuleInstalled(context, client)
            val result = client.process(InputImage.fromBitmap(source, 0)).awaitTask()
            val maskBuffer = result.foregroundConfidenceMask
                ?: error("Mask confidence ML Kit kosong")
            val pixelCount = width * height
            val confidences = FloatArray(pixelCount)
            maskBuffer.rewind()
            require(maskBuffer.remaining() >= pixelCount) {
                "Ukuran mask ML Kit (${maskBuffer.remaining()}) tidak sesuai $pixelCount piksel."
            }
            maskBuffer.get(confidences)
            withContext(Dispatchers.Default) {
                val alpha = AiLocalEngines.confidenceMaskToAlpha(
                    confidences = confidences,
                    width = width,
                    height = height,
                    featherPx = request.featherPx
                )
                AiLocalEngines.compositeAlphaMask(source, alpha, request.backgroundColorHex)
            }
        } finally {
            runCatching { client.close() }
        }
    }
}

/**
 * Orkestrator segmentasi: menjalankan [fullSegmenter] secara default dan otomatis fallback ke
 * [basicSegmenter] bila modul ML Kit / Play Services gagal dimuat atau gagal memproses.
 */
class ResilientBackgroundSegmenter(
    private val fullSegmenter: BackgroundSegmenter = MlKitSubjectSegmenter,
    private val basicSegmenter: BackgroundSegmenter = BasicFloodFillSegmenter
) {
    suspend fun removeBackground(
        context: Context,
        source: Bitmap,
        request: BackgroundRemovalRequest
    ): BackgroundRemovalResult {
        if (request.mode == SegmentationMode.BASIC) {
            val bitmap = basicSegmenter.segment(context, source, request)
            return BackgroundRemovalResult(
                bitmap = bitmap,
                engineUsed = basicSegmenter.engineKind,
                fallbackFromFull = false
            )
        }
        return try {
            val bitmap = fullSegmenter.segment(context, source, request)
            BackgroundRemovalResult(
                bitmap = bitmap,
                engineUsed = fullSegmenter.engineKind,
                fallbackFromFull = false
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val fallbackBitmap = basicSegmenter.segment(context, source, request)
            BackgroundRemovalResult(
                bitmap = fallbackBitmap,
                engineUsed = basicSegmenter.engineKind,
                fallbackFromFull = true,
                fallbackReason = t.localizedMessage?.take(120) ?: t::class.java.simpleName
            )
        }
    }
}

object BackgroundSegmenters {
    val default: ResilientBackgroundSegmenter by lazy { ResilientBackgroundSegmenter() }
}

private const val MODULE_DOWNLOAD_TIMEOUT_MS = 25_000L

private suspend fun ensureModuleInstalled(context: Context, client: SubjectSegmenter) {
    val api = client as? OptionalModuleApi ?: return
    val moduleClient = ModuleInstall.getClient(context)
    val availability = moduleClient.areModulesAvailable(api).awaitTask()
    if (availability.areModulesAvailable()) return

    withTimeout(MODULE_DOWNLOAD_TIMEOUT_MS) {
        suspendCancellableCoroutine<Unit> { cont ->
            var listenerRef: InstallStatusListener? = null
            val listener = InstallStatusListener { update ->
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                        listenerRef?.let { runCatching { moduleClient.unregisterListener(it) } }
                        if (cont.isActive) cont.resume(Unit)
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                        listenerRef?.let { runCatching { moduleClient.unregisterListener(it) } }
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException(
                                    "Unduhan modul subject_segment Play Services gagal (kode=${update.errorCode})"
                                )
                            )
                        }
                    }
                }
            }
            listenerRef = listener
            cont.invokeOnCancellation {
                runCatching { moduleClient.unregisterListener(listener) }
            }
            val request = ModuleInstallRequest.newBuilder()
                .addApi(api)
                .setListener(listener)
                .build()
            moduleClient.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled() && cont.isActive) {
                        runCatching { moduleClient.unregisterListener(listener) }
                        cont.resume(Unit)
                    }
                }
                .addOnFailureListener { error ->
                    runCatching { moduleClient.unregisterListener(listener) }
                    if (cont.isActive) cont.resumeWithException(error)
                }
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
