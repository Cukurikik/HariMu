package com.harimu.app.core.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
enum class UploadStatus { QUEUED, RUNNING, DONE, FAILED }

@Serializable
data class UploadTask(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val displayName: String,
    val mimeType: String,
    val provider: CloudProvider,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val status: UploadStatus = UploadStatus.QUEUED,
    val lastError: String = "",
    val remoteUrl: String = "",
    val bytes: Long = 0L,
    val sourceFilePath: String = ""
)

/** Kebijakan retry murni (diuji unit): 3 percobaan, jeda 2 s → 8 s → 30 s. */
object UploadRetryPolicy {
    const val MAX_ATTEMPTS = 3
    fun delayMillis(attempt: Int): Long = when (attempt) {
        1 -> 2_000L
        2 -> 8_000L
        else -> 30_000L
    }

    fun shouldRetry(task: UploadTask, error: CloudException?): Boolean {
        if (task.attempts >= MAX_ATTEMPTS) return false
        return error == null || error.retryable
    }
}

/**
 * Antrean unggah persisten (CORE-09).
 *
 * Berkas hasil tool disalin ke `filesDir/cloud_queue/` agar tetap ada walau cache dibersihkan.
 * Antrean disimpan sebagai JSON di `filesDir/cloud/queue.json` (bukan DataStore) dan diproses
 * berurutan dengan retry eksponensial. Status dapat diamati secara reaktif lewat [tasksFlow]
 * atau dibaca seketika lewat [snapshot].
 *
 * Batas jujur:
 * - Antrean berjalan selama proses aplikasi hidup. Setelah proses mati, tugas berstatus QUEUED
 *   dilanjutkan saat [processPending] dipanggil lagi (dari UI/Pengaturan), bukan oleh WorkManager.
 * - Tidak ada unggahan paralel; satu berkas pada satu waktu agar hemat data.
 */
class UploadQueue(
    context: Context,
    private val uploader: CloudUploader = CloudUploader(),
    private val sleeper: suspend (Long) -> Unit = { delay(it) }
) {
    private val queueDir = File(context.filesDir, "cloud_queue").apply { mkdirs() }
    private val stateFile = File(context.filesDir, "cloud/queue.json").apply { parentFile?.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val mutex = Mutex()
    private val _tasksFlow = MutableStateFlow(readLocked())
    val tasksFlow: StateFlow<List<UploadTask>> = _tasksFlow.asStateFlow()

    suspend fun snapshot(): List<UploadTask> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = readLocked()
            _tasksFlow.value = loaded
            loaded
        }
    }

    suspend fun enqueue(file: File, mimeType: String, provider: CloudProvider): UploadTask =
        withContext(Dispatchers.IO) {
            require(file.exists()) { "Berkas tidak ditemukan" }
            val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val copied = File(queueDir, "${System.currentTimeMillis()}_$safeName")
            file.copyTo(copied, overwrite = true)
            val task = UploadTask(
                filePath = copied.absolutePath,
                displayName = file.name,
                mimeType = mimeType,
                provider = provider,
                bytes = copied.length(),
                sourceFilePath = file.absolutePath
            )
            mutex.withLock {
                val tasks = readLocked().toMutableList()
                tasks.add(0, task)
                writeLocked(tasks)
            }
            task
        }

    suspend fun removeTask(taskId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tasks = readLocked()
            tasks.firstOrNull { it.id == taskId }?.let { removed ->
                File(removed.filePath).delete()
            }
            writeLocked(tasks.filterNot { it.id == taskId })
        }
    }

    suspend fun clearFinished() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tasks = readLocked()
            tasks.filter { it.status == UploadStatus.DONE || it.status == UploadStatus.FAILED }
                .forEach { File(it.filePath).delete() }
            writeLocked(tasks.filter { it.status == UploadStatus.QUEUED || it.status == UploadStatus.RUNNING })
        }
    }

    /**
     * Proses semua tugas QUEUED. [configProvider] dipanggil tiap tugas agar token hasil refresh
     * langsung dipakai; [persistConfig] menyimpan token baru secara terenkripsi.
     */
    suspend fun processPending(
        configProvider: suspend () -> CloudConfig,
        persistConfig: suspend (CloudConfig) -> Unit,
        onProgress: suspend (UploadTask) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var uploaded = 0
        val initial = mutex.withLock { readLocked() }
        for (original in initial.filter { it.status == UploadStatus.QUEUED }) {
            var task = original.copy(status = UploadStatus.RUNNING)
            update(task)
            onProgress(task)
            var lastError: CloudException? = null
            while (true) {
                val config = runCatching { configProvider() }
                    .getOrElse { error ->
                        lastError = CloudException(error.message ?: "Konfigurasi tidak terbaca", retryable = false)
                        null
                    }
                if (config == null) break
                try {
                    val refreshed = uploader.refreshTokenIfNeeded(config)
                    if (refreshed != config) persistConfig(refreshed)
                    val outcome = uploader.upload(File(task.filePath), task.displayName, task.mimeType, refreshed)
                    task = task.copy(
                        status = UploadStatus.DONE,
                        remoteUrl = outcome.remoteUrl,
                        bytes = outcome.bytes,
                        lastError = ""
                    )
                    val finished = File(task.filePath)
                    finished.delete()
                    uploaded++
                    lastError = null
                    break
                } catch (error: CloudException) {
                    lastError = error
                    task = task.copy(attempts = task.attempts + 1, lastError = error.message.orEmpty())
                    if (!UploadRetryPolicy.shouldRetry(task, error)) break
                    update(task)
                    onProgress(task)
                    sleeper(UploadRetryPolicy.delayMillis(task.attempts))
                } catch (error: Exception) {
                    lastError = CloudException(error.message ?: "Unggah gagal", retryable = false, cause = error)
                    task = task.copy(attempts = task.attempts + 1, lastError = lastError.message.orEmpty())
                    break
                }
            }
            if (task.status != UploadStatus.DONE) {
                task = task.copy(status = UploadStatus.FAILED, lastError = lastError?.message ?: task.lastError)
            }
            update(task)
            onProgress(task)
        }
        uploaded
    }

    suspend fun retryFailed(
        configProvider: suspend () -> CloudConfig,
        persistConfig: suspend (CloudConfig) -> Unit,
        onProgress: suspend (UploadTask) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tasks = readLocked().map {
                if (it.status == UploadStatus.FAILED) it.copy(status = UploadStatus.QUEUED, attempts = 0, lastError = "") else it
            }
            writeLocked(tasks)
        }
        processPending(configProvider, persistConfig, onProgress)
    }

    suspend fun retryTask(
        taskId: String,
        configProvider: suspend () -> CloudConfig,
        persistConfig: suspend (CloudConfig) -> Unit,
        onProgress: suspend (UploadTask) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val tasks = readLocked().map {
                if (it.id == taskId && it.status == UploadStatus.FAILED) {
                    it.copy(status = UploadStatus.QUEUED, attempts = 0, lastError = "")
                } else {
                    it
                }
            }
            writeLocked(tasks)
        }
        processPending(configProvider, persistConfig, onProgress)
    }

    private suspend fun update(task: UploadTask) = mutex.withLock {
        val tasks = readLocked().toMutableList()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task else tasks.add(0, task)
        writeLocked(tasks)
    }

    private fun readLocked(): List<UploadTask> = runCatching {
        if (!stateFile.exists()) emptyList()
        else json.decodeFromString<List<UploadTask>>(stateFile.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    private fun writeLocked(tasks: List<UploadTask>) {
        stateFile.parentFile?.mkdirs()
        val temp = File(stateFile.parentFile, "${stateFile.name}.part")
        temp.writeText(json.encodeToString(tasks), Charsets.UTF_8)
        if (!temp.renameTo(stateFile)) {
            stateFile.writeText(json.encodeToString(tasks), Charsets.UTF_8)
            temp.delete()
        }
        _tasksFlow.value = tasks
    }

    companion object {
        fun findLatestForFile(filePath: String, tasks: List<UploadTask>): UploadTask? {
            if (filePath.isBlank()) return null
            return tasks.firstOrNull { it.sourceFilePath == filePath || it.filePath == filePath }
        }
    }
}
