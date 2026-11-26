package com.harimu.app.core.prayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Satu kota/kabupaten yang dikenali layanan jadwal sholat. */
data class PrayerCity(val id: String, val name: String)

/** Jadwal sholat satu hari untuk satu kota. */
data class PrayerSchedule(
    val cityId: String,
    val city: String,
    val region: String,
    val dateLabel: String,
    val isoDate: String,
    val imsak: String,
    val subuh: String,
    val terbit: String,
    val dhuha: String,
    val dzuhur: String,
    val ashar: String,
    val maghrib: String,
    val isya: String
)

enum class PrayerErrorKind { EMPTY_QUERY, CITY_NOT_FOUND, HTTP, INVALID_RESPONSE, NETWORK }

class PrayerTimesException(
    val kind: PrayerErrorKind,
    val httpCode: Int = 0,
    cause: Throwable? = null
) : Exception(kind.name, cause)

/** Pesan siap tampil untuk setiap jenis kegagalan (tanpa pernah mengarang jadwal). */
fun PrayerTimesException.userMessage(isEnglish: Boolean): String = when (kind) {
    PrayerErrorKind.EMPTY_QUERY -> if (isEnglish) {
        "Enter a city name first, for example Jakarta or Yogyakarta."
    } else {
        "Masukkan nama kota terlebih dahulu, misalnya Jakarta atau Yogyakarta."
    }
    PrayerErrorKind.CITY_NOT_FOUND -> if (isEnglish) {
        "No city matched that name. Try another spelling, e.g. Bandung or Surabaya."
    } else {
        "Tidak ada kota yang cocok. Coba ejaan lain, misalnya Bandung atau Surabaya."
    }
    PrayerErrorKind.HTTP -> if (isEnglish) {
        "The prayer-times service answered HTTP $httpCode. Try again in a moment."
    } else {
        "Layanan jadwal sholat menjawab HTTP $httpCode. Coba lagi beberapa saat."
    }
    PrayerErrorKind.INVALID_RESPONSE -> if (isEnglish) {
        "The prayer-times service returned an unreadable response."
    } else {
        "Layanan jadwal sholat mengembalikan respons yang tidak bisa dibaca."
    }
    PrayerErrorKind.NETWORK -> if (isEnglish) {
        "No internet connection to api.myquran.com. Check your network and retry."
    } else {
        "Tidak ada koneksi ke api.myquran.com. Periksa jaringan lalu coba lagi."
    }
}

/**
 * Data source jadwal sholat (Quran API ID — `api.myquran.com`, tanpa autentikasi).
 * Dokumentasi: https://github.com/renomureza/quran-api-id
 */
interface PrayerTimesApi {
    suspend fun findCities(keyword: String): List<PrayerCity>
    suspend fun schedule(cityId: String, date: LocalDate): PrayerSchedule
}

class HttpPrayerTimesApi(
    baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) : PrayerTimesApi {

    private val rootUrl = baseUrl.trimEnd('/')

    override suspend fun findCities(keyword: String): List<PrayerCity> =
        PrayerTimesJson.parseCities(
            get("v2", "sholat", "kota", "cari", keyword.trim())
        )

    override suspend fun schedule(cityId: String, date: LocalDate): PrayerSchedule =
        PrayerTimesJson.parseSchedule(
            get("v2", "sholat", "jadwal", cityId, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        )

    private suspend fun get(vararg segments: String): String = withContext(Dispatchers.IO) {
        val url: HttpUrl = "$rootUrl/".toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw PrayerTimesException(PrayerErrorKind.HTTP, httpCode = response.code)
            }
            body
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.myquran.com"
    }
}

/** Pemetaan JSON mentah ke model domain; dipisah agar bisa diuji tanpa jaringan. */
object PrayerTimesJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseCities(body: String): List<PrayerCity> {
        val response = decode<CitiesResponse>(body)
        if (!response.status) throw PrayerTimesException(PrayerErrorKind.INVALID_RESPONSE)
        return response.data.mapNotNull { dto ->
            if (dto.id.isBlank() || dto.lokasi.isBlank()) null else PrayerCity(dto.id.trim(), dto.lokasi.trim())
        }
    }

    fun parseSchedule(body: String): PrayerSchedule {
        val response = decode<ScheduleResponse>(body)
        val data = response.data
        val times = data?.jadwal
        if (!response.status || data == null || times == null) {
            throw PrayerTimesException(PrayerErrorKind.INVALID_RESPONSE)
        }
        val schedule = PrayerSchedule(
            cityId = data.id.toString(),
            city = data.lokasi.trim(),
            region = data.daerah.trim(),
            dateLabel = times.tanggal.trim(),
            isoDate = times.date.trim(),
            imsak = times.imsak.trim(),
            subuh = times.subuh.trim(),
            terbit = times.terbit.trim(),
            dhuha = times.dhuha.trim(),
            dzuhur = times.dzuhur.trim(),
            ashar = times.ashar.trim(),
            maghrib = times.maghrib.trim(),
            isya = times.isya.trim()
        )
        if (schedule.subuh.isBlank() && schedule.maghrib.isBlank()) {
            throw PrayerTimesException(PrayerErrorKind.INVALID_RESPONSE)
        }
        return schedule
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (t: Throwable) {
        if (t is IOException) throw t
        throw PrayerTimesException(PrayerErrorKind.INVALID_RESPONSE, cause = t)
    }
}

@Serializable
private data class CitiesResponse(
    val status: Boolean = false,
    val data: List<CityDto> = emptyList()
)

@Serializable
private data class CityDto(
    val id: String = "",
    val lokasi: String = ""
)

@Serializable
private data class ScheduleResponse(
    val status: Boolean = false,
    val data: ScheduleDataDto? = null
)

@Serializable
private data class ScheduleDataDto(
    val id: Int = 0,
    val lokasi: String = "",
    val daerah: String = "",
    val jadwal: ScheduleTimesDto? = null
)

@Serializable
private data class ScheduleTimesDto(
    val tanggal: String = "",
    val imsak: String = "",
    val subuh: String = "",
    val terbit: String = "",
    val dhuha: String = "",
    val dzuhur: String = "",
    val ashar: String = "",
    val maghrib: String = "",
    val isya: String = "",
    val date: String = ""
)
