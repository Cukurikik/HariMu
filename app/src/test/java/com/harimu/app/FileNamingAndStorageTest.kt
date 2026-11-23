package com.harimu.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.harimu.app.core.files.FileStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FileNamingAndStorageTest {

    @Test
    fun outputFileNameFollowsHariMuConvention() {
        val fixedMillis = 1_763_900_000_000L
        val expectedStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(fixedMillis))

        val name = FileStorageManager.buildOutputFileName("VID-01", ".mp4", fixedMillis)
        assertEquals("HariMu_VID-01_${expectedStamp}.mp4", name)

        val sanitized = FileStorageManager.buildOutputFileName("  pdf merge/01 ", "PDF", fixedMillis)
        assertEquals("HariMu_pdf_merge_01_${expectedStamp}.PDF", sanitized)

        val blankFallback = FileStorageManager.buildOutputFileName("   ", "", fixedMillis)
        assertEquals("HariMu_TOOL_${expectedStamp}.bin", blankFallback)
    }

    @Test
    fun formatBytesFormatsAllScales() {
        assertEquals("512 B", FileStorageManager.companionFormatBytes(512L))
        assertEquals("1.5 KB", FileStorageManager.companionFormatBytes(1536L))
        assertEquals("2.00 MB", FileStorageManager.companionFormatBytes(2L * 1024L * 1024L))
        assertEquals("1.00 GB", FileStorageManager.companionFormatBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun tempDirectoryAndDiskSpaceChecksWorkAndCleanUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FileStorageManager(context)

        val jobTemp = storage.createJobTempDir("job-123")
        val tempFile = File(jobTemp, "scratch.tmp").apply { writeText("hello") }
        assertTrue(tempFile.exists())
        assertTrue(storage.tempCacheSizeBytes() >= 5L)

        assertTrue(storage.cleanupJobTemp("job-123"))
        assertFalse(jobTemp.exists())

        storage.createTempFile("leftover", "bin").writeText("abc")
        val freed = storage.clearTempCache()
        assertTrue(freed >= 3L)
        assertEquals(0L, storage.tempCacheSizeBytes())

        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 1024 * 1024, 512 * 1024, 512 * 1024)
        assertTrue(storage.availableDiskSpaceBytes() > FileStorageManager.MIN_FREE_DISK_BYTES)
        assertTrue(storage.hasEnoughDiskSpace())

        ShadowStatFs.registerStats(storage.outputsDir.absolutePath, 1024, 1, 1)
        assertFalse(storage.hasEnoughDiskSpace())
    }
}
