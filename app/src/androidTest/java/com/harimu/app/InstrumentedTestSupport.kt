package com.harimu.app

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Utilitas bersama untuk instrumented test.
 *
 * Aset diambil dari APK test (`src/androidTest/assets`) lalu disalin ke `cacheDir/samples` supaya
 * tool mendapat `file://` URI yang bisa dibaca `ContentResolver.openInputStream`.
 */
object InstrumentedTestSupport {

    val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    val storage: FileStorageManager by lazy { FileStorageManager(context) }

    fun sampleFile(name: String): File {
        val dir = File(context.cacheDir, "samples").apply { mkdirs() }
        val dest = File(dir, name)
        if (!dest.exists() || dest.length() == 0L) {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            assets.open(name).use { input ->
                dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        }
        return dest
    }

    fun sampleUri(name: String): Uri = Uri.fromFile(sampleFile(name))

    fun newToolContext(isEnglish: Boolean = false): ToolContext = ToolContext(
        appContext = context,
        storage = storage,
        isEnglish = isEnglish
    )

    fun clearTemp() {
        storage.clearTempCache()
    }

    fun toolFlow(toolId: String, input: ToolInput): Flow<ToolProgress> {
        val tool = ToolRegistry.findById(toolId) ?: error("Tool $toolId tidak terdaftar di ToolRegistry")
        return tool.run(newToolContext(), input)
    }

    /**
     * Menjalankan tool sampai selesai dan mengembalikan [ToolResult].
     * Melempar [IllegalStateException] bila tool mengirim `Failed` (supaya penyebabnya terlihat).
     */
    suspend fun runTool(toolId: String, input: ToolInput): ToolResult {
        var result: ToolResult? = null
        var failure: String? = null
        toolFlow(toolId, input).collect { progress ->
            when (progress) {
                is ToolProgress.Done -> result = progress.result
                is ToolProgress.Failed -> failure = progress.error
                else -> Unit
            }
        }
        return result ?: throw IllegalStateException("Tool $toolId gagal: ${failure ?: "tidak menghasilkan output"}")
    }

    fun outputUri(result: ToolResult): Uri {
        val file = result.outputs.firstOrNull()?.file
            ?: throw AssertionError("Tool tidak menghasilkan berkas")
        return Uri.fromFile(file)
    }

    fun singleOutput(result: ToolResult): File {
        val file = result.outputs.firstOrNull()?.file
            ?: throw AssertionError("Tool tidak menghasilkan berkas")
        if (!file.exists() || file.length() <= 0L) {
            throw AssertionError("Berkas keluaran kosong: ${file.absolutePath}")
        }
        return file
    }
}
