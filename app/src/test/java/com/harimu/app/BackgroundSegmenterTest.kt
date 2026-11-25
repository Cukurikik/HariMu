package com.harimu.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.ai.AiLocalEngines
import com.harimu.app.core.ai.BackgroundRemovalRequest
import com.harimu.app.core.ai.BackgroundSegmenter
import com.harimu.app.core.ai.BasicFloodFillSegmenter
import com.harimu.app.core.ai.ResilientBackgroundSegmenter
import com.harimu.app.core.ai.SegmentationEngineUsed
import com.harimu.app.core.ai.SegmentationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackgroundSegmenterTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }

    private fun sampleScene(): Bitmap {
        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in 8 until 16) {
            for (x in 8 until 16) {
                bmp.setPixel(x, y, Color.rgb(20, 60, 200))
            }
        }
        return bmp
    }

    @Test
    fun confidenceMaskToAlphaMapsProbabilitiesAndFeathers() {
        val mask = floatArrayOf(
            0f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 0f
        )
        val sharp = AiLocalEngines.confidenceMaskToAlpha(mask, 3, 3, featherPx = 0)
        assertEquals(0, sharp[0])
        assertEquals(255, sharp[4])

        val feathered = AiLocalEngines.confidenceMaskToAlpha(mask, 3, 3, featherPx = 1)
        assertTrue(feathered[0] > 0)
        assertTrue(feathered[4] < 255)
    }

    @Test
    fun modeParamDefaultsToFullAndRecognizesBasic() {
        assertEquals(SegmentationMode.FULL, SegmentationMode.fromParam("Penuh (ML Kit)"))
        assertEquals(SegmentationMode.FULL, SegmentationMode.fromParam(""))
        assertEquals(SegmentationMode.BASIC, SegmentationMode.fromParam("Dasar (Heuristik)"))
        assertEquals(SegmentationMode.BASIC, SegmentationMode.fromParam("Basic"))
    }

    @Test
    fun resilientSegmenterUsesFullEngineWhenAvailable() = runBlocking {
        var fullCalls = 0
        var basicCalls = 0
        val fakeFull = object : BackgroundSegmenter {
            override val engineKind = SegmentationEngineUsed.MLKIT_SUBJECT
            override suspend fun segment(
                context: Context,
                source: Bitmap,
                request: BackgroundRemovalRequest
            ): Bitmap {
                fullCalls++
                return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            }
        }
        val fakeBasic = object : BackgroundSegmenter {
            override val engineKind = SegmentationEngineUsed.BASIC_FLOOD_FILL
            override suspend fun segment(
                context: Context,
                source: Bitmap,
                request: BackgroundRemovalRequest
            ): Bitmap {
                basicCalls++
                return BasicFloodFillSegmenter.segment(context, source, request)
            }
        }

        val src = sampleScene()
        val outcome = ResilientBackgroundSegmenter(fakeFull, fakeBasic).removeBackground(
            context = context,
            source = src,
            request = BackgroundRemovalRequest(mode = SegmentationMode.FULL)
        )
        assertEquals(1, fullCalls)
        assertEquals(0, basicCalls)
        assertEquals(SegmentationEngineUsed.MLKIT_SUBJECT, outcome.engineUsed)
        assertFalse(outcome.fallbackFromFull)
        outcome.bitmap.recycle()
        src.recycle()
    }

    @Test
    fun resilientSegmenterFallsBackToBasicWhenMlKitFails() = runBlocking {
        val failingFull = object : BackgroundSegmenter {
            override val engineKind = SegmentationEngineUsed.MLKIT_SUBJECT
            override suspend fun segment(
                context: Context,
                source: Bitmap,
                request: BackgroundRemovalRequest
            ): Bitmap {
                throw IllegalStateException("Play Services tidak tersedia")
            }
        }

        val src = sampleScene()
        val outcome = ResilientBackgroundSegmenter(failingFull, BasicFloodFillSegmenter).removeBackground(
            context = context,
            source = src,
            request = BackgroundRemovalRequest(mode = SegmentationMode.FULL)
        )
        assertEquals(SegmentationEngineUsed.BASIC_FLOOD_FILL, outcome.engineUsed)
        assertTrue(outcome.fallbackFromFull)
        assertTrue(outcome.fallbackReason.orEmpty().contains("Play Services"))
        // Border should be transparent, center subject pixel opaque.
        assertEquals(0, Color.alpha(outcome.bitmap.getPixel(0, 0)))
        assertEquals(255, Color.alpha(outcome.bitmap.getPixel(12, 12)))
        outcome.bitmap.recycle()
        src.recycle()
    }

    @Test
    fun basicModeSkipsFullEngineAndPreservesSubject() = runBlocking {
        var fullCalled = false
        val fakeFull = object : BackgroundSegmenter {
            override val engineKind = SegmentationEngineUsed.MLKIT_SUBJECT
            override suspend fun segment(
                context: Context,
                source: Bitmap,
                request: BackgroundRemovalRequest
            ): Bitmap {
                fullCalled = true
                return source
            }
        }

        val src = sampleScene()
        val outcome = ResilientBackgroundSegmenter(fakeFull, BasicFloodFillSegmenter).removeBackground(
            context = context,
            source = src,
            request = BackgroundRemovalRequest(mode = SegmentationMode.BASIC, featherPx = 0)
        )
        assertFalse(fullCalled)
        assertFalse(outcome.fallbackFromFull)
        assertEquals(SegmentationEngineUsed.BASIC_FLOOD_FILL, outcome.engineUsed)
        assertEquals(0, Color.alpha(outcome.bitmap.getPixel(0, 0)))
        assertEquals(255, Color.alpha(outcome.bitmap.getPixel(12, 12)))
        outcome.bitmap.recycle()
        src.recycle()
    }

    @Test(expected = CancellationException::class)
    fun resilientSegmenterPropagatesCancellationWithoutFallback() {
        runBlocking {
            val cancellingFull = object : BackgroundSegmenter {
                override val engineKind = SegmentationEngineUsed.MLKIT_SUBJECT
                override suspend fun segment(
                    context: Context,
                    source: Bitmap,
                    request: BackgroundRemovalRequest
                ): Bitmap {
                    throw CancellationException("Dibatalkan")
                }
            }
            val src = sampleScene()
            try {
                ResilientBackgroundSegmenter(cancellingFull, BasicFloodFillSegmenter).removeBackground(
                    context = context,
                    source = src,
                    request = BackgroundRemovalRequest(mode = SegmentationMode.FULL)
                )
            } finally {
                src.recycle()
            }
        }
    }
}
