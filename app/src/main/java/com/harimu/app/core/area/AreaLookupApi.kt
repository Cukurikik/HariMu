package com.harimu.app.core.area

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Satu baris alamat: desa/kelurahan lengkap dengan kecamatan, kabupaten/kota, provinsi, dan kode pos. */
data class AreaEntry(
    val negara: String,
    val provinsi: String,
    val kabkota: String,
    val kecamatan: String,
    val desakel: String,
    val kodepos: String
)

enum class AreaErrorKind { EMPTY_QUERY, TOO_SHORT, HTTP, INVALID_RESPONSE, NETWORK }

class AreaLookupException(
    val kind: AreaErrorKind,
    val httpCode: Int = 0,
    cause: Throwable? = null
) : Exception(kind.name, cause)

/** Pesan siap tampil untuk setiap jenis kegagalan (tanpa pernah mengarang wilayah). */
fun AreaLookupException.userMessage(isEnglish: Boolean): String = when (kind) {
    AreaErrorKind.EMPTY_QUERY -> if (isEnglish) {
        "Enter an area, district, city, or postal code first, for example Soreang."
    } else {
        "Masukkan nama desa, kecamatan, kota, atau kode pos terlebih dahulu, misalnya Soreang."
    }
    AreaErrorKind.TOO_SHORT -> if (isEnglish) {
        "Use at least 2 characters so the search can match an area."
    } else {
        "Gunakan minimal 2 huruf agar pencarian bisa menemukan wilayah."
    }
    AreaErrorKind.HTTP -> if (isEnglish) {
        "The address service answered HTTP $httpCode. Try again in a moment."
    } else {
        "Layanan alamat menjawab HTTP $httpCode. Coba lagi beberapa saat."
    }
    AreaErrorKind.INVALID_RESPONSE -> if (isEnglish) {
        "The address service returned an unreadable response."
    } else {
        "Layanan alamat mengembalikan respons yang tidak bisa dibaca."
    }
    AreaErrorKind.NETWORK -> if (isEnglish) {
        "No internet connection to alamat.thecloudalert.com. Check your network and retry."
    } else {
        "Tidak ada koneksi ke alamat.thecloudalert.com. Periksa jaringan lalu coba lagi."
    }
}

/**
 * Data source pencarian alamat Indonesia (Indonesian Address Provider API — `alamat.thecloudalert.com`,
 * tanpa autentikasi). Dokumentasi: https://alamat.thecloudalert.com
 */
interface AreaLookupApi {
    suspend fun search(keyword: String): List<AreaEntry>
}

class HttpAreaLookupApi(
    baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) : AreaLookupApi {

    private val rootUrl = baseUrl.trimEnd('/')

    override suspend fun search(keyword: String): List<AreaEntry> =
        AreaLookupJson.parseSearch(get(keyword.trim()))

    private suspend fun get(keyword: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(searchUrl(rootUrl, keyword))
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AreaLookupException(AreaErrorKind.HTTP, httpCode = response.code)
            }
            body
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://alamat.thecloudalert.com"

        /** Bangun URL `api/cari/index/?keyword=…` dengan keyword yang sudah di-encode. */
        internal fun searchUrl(baseUrl: String, keyword: String): HttpUrl =
            "${baseUrl.trimEnd('/')}/".toHttpUrl().newBuilder()
                .addPathSegments("api/cari/index/")
                .addQueryParameter("keyword", keyword)
                .build()
    }
}

/** Pemetaan JSON mentah ke model domain; dipisah agar bisa diuji tanpa jaringan. */
object AreaLookupJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseSearch(body: String): List<AreaEntry> {
        val response = try {
            json.decodeFromString<SearchResponse>(body)
        } catch (t: Throwable) {
            if (t is IOException) throw t
            throw AreaLookupException(AreaErrorKind.INVALID_RESPONSE, cause = t)
        }
        if (response.status != 200) throw AreaLookupException(AreaErrorKind.INVALID_RESPONSE)
        return response.result.mapNotNull { dto ->
            val entry = AreaEntry(
                negara = dto.negara.trim(),
                provinsi = dto.provinsi.trim(),
                kabkota = dto.kabkota.trim(),
                kecamatan = dto.kecamatan.trim(),
                desakel = dto.desakel.trim(),
                kodepos = dto.kodepos.trim()
            )
            if (entry.desakel.isBlank() && entry.kecamatan.isBlank() && entry.kodepos.isBlank()) null else entry
        }
    }
}

@Serializable
private data class SearchResponse(
    val status: Int = 0,
    val message: String = "",
    val result: List<AreaDto> = emptyList()
)

@Serializable
private data class AreaDto(
    val negara: String = "",
    val provinsi: String = "",
    val kabkota: String = "",
    val kecamatan: String = "",
    val desakel: String = "",
    val kodepos: String = ""
)
