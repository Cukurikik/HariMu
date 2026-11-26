package com.harimu.app.core.nusantara

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class NusantaraErrorKind { HTTP, INVALID_RESPONSE, NETWORK }

class NusantaraException(
    val kind: NusantaraErrorKind,
    /** Nama layanan yang ditampilkan ke pengguna, mis. "BMKG" atau "emsifa". */
    val service: String,
    val httpCode: Int = 0,
    cause: Throwable? = null
) : Exception(kind.name, cause)

/** Pesan siap tampil untuk setiap kegagalan layanan data publik Indonesia. */
fun NusantaraException.userMessage(isEnglish: Boolean): String = when (kind) {
    NusantaraErrorKind.HTTP -> if (isEnglish) {
        "The $service service answered HTTP $httpCode. Try again in a moment."
    } else {
        "Layanan $service menjawab HTTP $httpCode. Coba lagi beberapa saat."
    }
    NusantaraErrorKind.INVALID_RESPONSE -> if (isEnglish) {
        "The $service service returned an unreadable response."
    } else {
        "Layanan $service mengembalikan respons yang tidak bisa dibaca."
    }
    NusantaraErrorKind.NETWORK -> if (isEnglish) {
        "No internet connection to $service. Check your network and retry."
    } else {
        "Tidak ada koneksi ke $service. Periksa jaringan lalu coba lagi."
    }
}

/** Klien HTTP bersama untuk seluruh data publik Indonesia (tanpa kunci API). */
object NusantaraHttp {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun url(baseUrl: String, vararg segments: String): HttpUrl =
        "${baseUrl.trimEnd('/')}/".toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegments(it.trim('/')) }
        }.build()

    suspend fun getText(url: String, service: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw NusantaraException(NusantaraErrorKind.HTTP, service, httpCode = response.code)
            }
            body
        }
    }

    /** Bungkus panggilan jaringan menjadi `Result` dengan pemetaan error yang seragam. */
    suspend fun <T> run(service: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        Result.failure(
            t as? NusantaraException ?: NusantaraException(
                kind = if (t is IOException) NusantaraErrorKind.NETWORK else NusantaraErrorKind.INVALID_RESPONSE,
                service = service,
                cause = t
            )
        )
    }
}

internal val nusantaraJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal inline fun <reified T> decodeNusantara(body: String, service: String): T = try {
    nusantaraJson.decodeFromString<T>(body)
} catch (t: Throwable) {
    if (t is IOException) throw t
    throw NusantaraException(NusantaraErrorKind.INVALID_RESPONSE, service, cause = t)
}
