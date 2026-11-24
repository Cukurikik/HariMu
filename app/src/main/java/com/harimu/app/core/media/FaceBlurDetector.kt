package com.harimu.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FacePreviewSample(
    val bitmap: Bitmap,
    val faces: List<FaceBox>,
    val timeMs: Long
)

data class FaceSamplingResult(
    val detectWidth: Int,
    val detectHeight: Int,
    val samples: List<TimedFaces>
)

object FaceBlurDetector {

    private fun createDetector(): FaceDetector {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
        return FaceDetection.getClient(opts)
    }

    suspend fun detectFacesAtTime(
        context: Context,
        uri: Uri,
        timeMs: Long,
        maxDimension: Int = 640
    ): FacePreviewSample? {
        val mmr = MediaMetadataRetriever()
        val detector = createDetector()
        return try {
            mmr.setDataSource(context, uri)
            val frame = extractScaledFrame(mmr, timeMs * 1000L, maxDimension) ?: return null
            val faces = detectOnBitmap(detector, frame)
            FacePreviewSample(bitmap = frame, faces = faces, timeMs = timeMs)
        } finally {
            runCatching { mmr.release() }
            runCatching { detector.close() }
        }
    }

    suspend fun sampleVideoFaces(
        context: Context,
        uri: Uri,
        durationMs: Long,
        samplingFps: Int = 2,
        maxDimension: Int = 640,
        onProgress: suspend (percent: Int) -> Unit = {}
    ): FaceSamplingResult {
        val safeDurMs = durationMs.coerceAtLeast(500L)
        val stepMs = (1000L / samplingFps.coerceIn(1, 4)).coerceAtLeast(250L)
        val totalSamples = ((safeDurMs / stepMs).toInt() + 1).coerceIn(1, 180)
        val mmr = MediaMetadataRetriever()
        val detector = createDetector()
        var detectW = 0
        var detectH = 0
        val collected = mutableListOf<TimedFaces>()

        try {
            mmr.setDataSource(context, uri)
            for (idx in 0 until totalSamples) {
                currentCoroutineContext().ensureActive()
                val tMs = ((idx.toLong() * safeDurMs) / totalSamples.coerceAtLeast(1)).coerceAtMost(safeDurMs)
                val frame = extractScaledFrame(mmr, tMs * 1000L, maxDimension)
                if (frame != null) {
                    try {
                        if (detectW == 0) {
                            detectW = frame.width
                            detectH = frame.height
                        }
                        val boxes = detectOnBitmap(detector, frame)
                        if (boxes.isNotEmpty()) {
                            collected.add(TimedFaces(timeMs = tMs, faces = boxes))
                        }
                    } finally {
                        if (!frame.isRecycled) frame.recycle()
                    }
                }
                val pct = 10 + ((idx + 1) * 45) / totalSamples.coerceAtLeast(1)
                onProgress(pct)
            }
        } finally {
            runCatching { mmr.release() }
            runCatching { detector.close() }
        }
        return FaceSamplingResult(
            detectWidth = detectW,
            detectHeight = detectH,
            samples = collected
        )
    }

    private suspend fun detectOnBitmap(detector: FaceDetector, bitmap: Bitmap): List<FaceBox> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).awaitResult()
        return faces.map { f ->
            val b = f.boundingBox
            FaceBox(
                left = b.left.coerceAtLeast(0),
                top = b.top.coerceAtLeast(0),
                width = b.width().coerceAtLeast(4),
                height = b.height().coerceAtLeast(4),
                trackingId = f.trackingId
            )
        }
    }

    private fun extractScaledFrame(
        mmr: MediaMetadataRetriever,
        timeUs: Long,
        maxDimension: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val scaled = runCatching {
                mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDimension, maxDimension)
            }.getOrNull()
            if (scaled != null) return scaled
        }
        val raw = runCatching {
            mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull() ?: return null
        val longSide = maxOf(raw.width, raw.height)
        if (longSide <= maxDimension) return raw
        val ratio = maxDimension.toFloat() / longSide.toFloat()
        val outW = (raw.width * ratio).toInt().coerceAtLeast(2)
        val outH = (raw.height * ratio).toInt().coerceAtLeast(2)
        val scaled = Bitmap.createScaledBitmap(raw, outW, outH, true)
        if (scaled != raw) raw.recycle()
        return scaled
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
