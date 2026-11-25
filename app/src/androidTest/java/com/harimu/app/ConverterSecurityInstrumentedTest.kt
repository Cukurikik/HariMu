package com.harimu.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harimu.app.core.engines.ArchiveEngine
import com.harimu.app.core.toolapi.ToolInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Converter: CNV-18 (ZIP AES-256, kata sandi salah harus ditolak) dan CNV-16 (zip-slip harus
 * dinetralkan saat ekstraksi).
 */
@RunWith(AndroidJUnit4::class)
class ConverterSecurityInstrumentedTest {

    private val support = InstrumentedTestSupport

    @Before
    fun setUp() {
        support.clearTemp()
    }

    @Test
    fun aesZipRejectsWrongPasswordAndAcceptsCorrectOne() = runBlocking {
        val source = support.sampleUri("sample_audio.wav")
        val correctPassword = "rahasia123"

        val encrypted = support.runTool(
            "CNV-18",
            ToolInput(
                uris = listOf(source),
                params = mapOf("mode" to "Enkripsi ZIP AES-256", "password" to correctPassword)
            )
        )
        val encryptedFile = support.singleOutput(encrypted)
        assertEquals("application/zip", encrypted.outputs.first().mimeType)

        val wrongAttempt = runCatching {
            support.runTool(
                "CNV-18",
                ToolInput(
                    uris = listOf(Uri.fromFile(encryptedFile)),
                    params = mapOf("mode" to "Dekripsi ZIP AES-256", "password" to "salahSekali99")
                )
            )
        }
        assertTrue("kata sandi salah harus gagal, bukan menghasilkan ZIP", wrongAttempt.isFailure)
        val message = wrongAttempt.exceptionOrNull()?.message.orEmpty()
        assertTrue("pesan kegagalan harus menjelaskan sebab, dapat: '$message'", message.isNotBlank())

        val decrypted = support.runTool(
            "CNV-18",
            ToolInput(
                uris = listOf(Uri.fromFile(encryptedFile)),
                params = mapOf("mode" to "Dekripsi ZIP AES-256", "password" to correctPassword)
            )
        )
        val decryptedFile = support.singleOutput(decrypted)
        ZipFile(decryptedFile).use { archive ->
            val names = archive.entries().toList().map { it.name }
            assertTrue("ZIP hasil dekripsi harus berisi entri, dapat: $names", names.isNotEmpty())
            assertTrue("entri harus berisi audio sampel", names.any { it.contains("sample_audio") })
        }
    }

    @Test
    fun zipSlipEntriesAreSanitizedDuringExtraction() = runBlocking {
        assertNotNull("entri normal harus diterima", ArchiveEngine.sanitizeEntryName("folder/ok.txt"))
        val sanitized = ArchiveEngine.sanitizeEntryName("../evil.txt")
        assertTrue(
            "entri zip-slip harus ditolak atau dinetralkan, dapat: $sanitized",
            sanitized == null || (!sanitized.contains("..") && !sanitized.startsWith("/"))
        )

        val zip = File(support.context.cacheDir, "samples/zipslip_${System.currentTimeMillis()}.zip")
        zip.parentFile?.mkdirs()
        ZipOutputStream(zip.outputStream()).use { out ->
            listOf("../evil.txt", "/absolute.txt", "folder/../traversal.txt", "ok.txt").forEach { name ->
                out.putNextEntry(ZipEntry(name))
                out.write("isi-$name".toByteArray())
                out.closeEntry()
            }
        }

        val result = support.runTool("CNV-16", ToolInput(uris = listOf(Uri.fromFile(zip))))
        val extracted = support.singleOutput(result)

        ZipFile(extracted).use { archive ->
            val names = archive.entries().toList().filter { !it.isDirectory }.map { it.name }
            assertTrue("ZIP hasil harus berisi ok.txt, dapat: $names", names.any { it.endsWith("ok.txt") })
            assertFalse(
                "nama entri hasil masih mengandung traversal: $names",
                names.any { it.contains("..") || it.startsWith("/") }
            )
        }

        val outsideTarget = File(support.storage.tempDir.parentFile, "evil.txt")
        assertFalse("zip-slip tidak boleh menulis di luar direktori kerja", outsideTarget.exists())
    }
}
