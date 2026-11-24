package com.harimu.app.core.cloud

import android.content.Context
import com.harimu.app.core.security.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Penyimpanan konfigurasi cloud (CORE-09). Seluruh [CloudConfig] — termasuk access key, secret,
 * dan refresh token — disegel dengan [SecretBox] (AES-256-GCM, kunci AndroidKeyStore) sebelum
 * ditulis ke `filesDir/cloud/credentials.bin`.
 *
 * Tidak ada kredensial di DataStore, log, nama berkas, atau backup .omni.
 */
class CloudCredentialStore(private val context: Context) {

    private val file = File(context.filesDir, "cloud/credentials.bin")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val mutex = Mutex()

    suspend fun load(): CloudConfig = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock CloudConfig()
            runCatching {
                val sealed = file.readText(Charsets.UTF_8)
                if (SecretBox.isSealed(sealed)) {
                    json.decodeFromString(CloudConfig.serializer(), SecretBox.unseal(sealed))
                } else {
                    // Data lama/rusak: jangan pernah menebak; mulai dari kosong.
                    CloudConfig()
                }
            }.getOrDefault(CloudConfig())
        }
    }

    suspend fun save(config: CloudConfig) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.parentFile?.mkdirs()
            val sealed = SecretBox.seal(json.encodeToString(config))
            val temp = File(file.parentFile, "${file.name}.part")
            temp.writeText(sealed, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(sealed, Charsets.UTF_8)
                temp.delete()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }
}
