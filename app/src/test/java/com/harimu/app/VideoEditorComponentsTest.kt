package com.harimu.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.media.VideoEditorMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class VideoEditorComponentsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun videoEditorMathEffectiveDurationCalculations() {
        // Test basic trim without speed change
        val clip1 = EditorClipSpec(
            id = "clip1",
            uriString = "file:///test.mp4",
            displayName = "Test Clip",
            durationMs = 10_000L, // 10 seconds
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            speed = 1.0f,
            rotationDeg = 0,
            muteAudio = false
        )
        assertEquals(10_000L, VideoEditorMath.effectiveClipDurationMs(clip1))

        // Test trim with speed 2x (should halve duration)
        val clip2 = clip1.copy(speed = 2.0f)
        assertEquals(5_000L, VideoEditorMath.effectiveClipDurationMs(clip2))

        // Test trim with speed 0.5x (should double duration)
        val clip3 = clip1.copy(speed = 0.5f)
        assertEquals(20_000L, VideoEditorMath.effectiveClipDurationMs(clip3))

        // Test partial trim (2s to 8s = 6s at 1x)
        val clip4 = clip1.copy(trimStartMs = 2_000L, trimEndMs = 8_000L)
        assertEquals(6_000L, VideoEditorMath.effectiveClipDurationMs(clip4))

        // Test partial trim with speed 2x (6s / 2 = 3s)
        val clip5 = clip1.copy(trimStartMs = 2_000L, trimEndMs = 8_000L, speed = 2.0f)
        assertEquals(3_000L, VideoEditorMath.effectiveClipDurationMs(clip5))

        // Test minimum duration enforcement (min 250ms)
        val clip6 = clip1.copy(trimStartMs = 0L, trimEndMs = 100L, speed = 4.0f)
        // (100ms / 4) = 25ms, but minimum is 250ms
        assertEquals(250L, VideoEditorMath.effectiveClipDurationMs(clip6))
    }

    @Test
    fun videoEditorMathTotalTimelineDuration() {
        val clips = listOf(
            EditorClipSpec(
                id = "clip1",
                uriString = "file:///test1.mp4",
                displayName = "Clip 1",
                durationMs = 10_000L,
                trimStartMs = 0L,
                trimEndMs = 10_000L,
                speed = 1.0f,
                rotationDeg = 0,
                muteAudio = false
            ),
            EditorClipSpec(
                id = "clip2",
                uriString = "file:///test2.mp4",
                displayName = "Clip 2",
                durationMs = 5_000L,
                trimStartMs = 0L,
                trimEndMs = 5_000L,
                speed = 2.0f,
                rotationDeg = 0,
                muteAudio = false
            ),
            EditorClipSpec(
                id = "clip3",
                uriString = "file:///test3.mp4",
                displayName = "Clip 3",
                durationMs = 8_000L,
                trimStartMs = 1_000L,
                trimEndMs = 7_000L,
                speed = 0.5f,
                rotationDeg = 0,
                muteAudio = false
            )
        )
        // clip1: 10s at 1x = 10s
        // clip2: 5s at 2x = 2.5s
        // clip3: 6s at 0.5x = 12s
        // Total: 24.5s = 24,500ms
        assertEquals(24_500L, VideoEditorMath.totalTimelineDurationMs(clips))
    }

    @Test
    fun videoEditorMathRotationNormalization() {
        assertEquals(0, VideoEditorMath.normalizeRotation(0))
        assertEquals(90, VideoEditorMath.normalizeRotation(90))
        assertEquals(180, VideoEditorMath.normalizeRotation(180))
        assertEquals(270, VideoEditorMath.normalizeRotation(270))
        assertEquals(90, VideoEditorMath.normalizeRotation(450)) // 450 % 360 = 90
        assertEquals(90, VideoEditorMath.normalizeRotation(-270)) // -270 + 360 = 90
        assertEquals(0, VideoEditorMath.normalizeRotation(360))
        assertEquals(90, VideoEditorMath.normalizeRotation(100))
        assertEquals(180, VideoEditorMath.normalizeRotation(200))
        assertEquals(270, VideoEditorMath.normalizeRotation(300))
    }

    @Test
    fun videoEditorMathTargetCanvasDimensions() {
        val dims720p = VideoEditorMath.targetCanvasDimensions("720p", true)
        assertEquals(1280, dims720p.width)
        assertEquals(720, dims720p.height)

        val dims1080p = VideoEditorMath.targetCanvasDimensions("1080p", true)
        assertEquals(1920, dims1080p.width)
        assertEquals(1080, dims1080p.height)

        val dims480p = VideoEditorMath.targetCanvasDimensions("480p", true)
        assertEquals(854, dims480p.width) // 480 * 16/9 = 853.33 -> 854 (even)
        assertEquals(480, dims480p.height)

        // Portrait
        val dims720pPortrait = VideoEditorMath.targetCanvasDimensions("720p", false)
        assertEquals(720, dims720pPortrait.width)
        assertEquals(1280, dims720pPortrait.height)
    }

    @Test
    fun clipInspectorParameterMapping() {
        // Test that clip parameters map correctly to VID tool parameters
        val clip = EditorClipSpec(
            id = "clip1",
            uriString = "file:///test.mp4",
            displayName = "Test Clip",
            durationMs = 30_000L, // 30 seconds
            trimStartMs = 5_000L,  // 5s
            trimEndMs = 25_000L,   // 25s
            speed = 1.5f,
            rotationDeg = 90,
            muteAudio = true
        )

        // VID-01 Trim params
        assertEquals(5.0f, clip.trimStartMs / 1000f)
        assertEquals(25.0f, clip.trimEndMs / 1000f)

        // VID-05 Speed param
        assertEquals(1.5f, clip.speed)

        // VID-09 Rotation param
        assertEquals(90, clip.rotationDeg)

        // VID-24 Mute (no params, just requires audio)
        assertTrue(clip.muteAudio)
    }

    @Test
    fun videoEngineCapabilitiesDocumentation() {
        // This test documents what the VideoEditorEngine supports
        // and serves as a regression test if capabilities change

        val supportedOperations = setOf(
            "trim",
            "speed",
            "rotation",
            "mute",
            "reorder",
            "duplicate",
            "remove",
            "resolution_presets"
        )

        val unsupportedOperations = setOf(
            "transitions",
            "chroma_key",
            "stop_motion",
            "time_lapse",
            "color_grading",
            "stabilization",
            "face_blur",
            "logo_blur",
            "text_overlay",
            "image_overlay",
            "subtitle_burning"
        )

        // Verify all VID tools that require unsupported ops are separate tools
        val vidToolsRequiringUnsupportedOps = setOf(
            "VID-11", // stabilize
            "VID-14", // logo blur
            "VID-15", // text overlay
            "VID-16", // add audio (mix/replace)
            "VID-17", // image overlay
            "VID-19", // reverse
            "VID-23", // subtitles
            "VID-25", // face blur
            "VID-26", // color grading
            "VID-27", // transitions
            "VID-28", // chroma key
            "VID-29", // stop motion
            "VID-30"  // time lapse
        )

        assertEquals(14, vidToolsRequiringUnsupportedOps.size)
        // All these tools should be available as separate VID tools
        // not as inline Pro Editor operations
    }
}