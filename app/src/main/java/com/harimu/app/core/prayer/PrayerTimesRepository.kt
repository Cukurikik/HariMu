package com.harimu.app.core.prayer

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.LocalDate

/**
 * Repository jadwal sholat: cari kota lalu ambil jadwalnya, dan ubah setiap kegagalan
 * (input kosong, kota tidak dikenal, HTTP error, JSON rusak, jaringan putus) menjadi
 * [PrayerTimesException] dengan pesan yang bisa ditampilkan. Tidak ada jadwal karangan.
 */
class PrayerTimesRepository(
    private val api: PrayerTimesApi = HttpPrayerTimesApi()
) {

    suspend fun load(cityQuery: String, date: LocalDate): Result<PrayerSchedule> {
        val keyword = cityQuery.trim()
        if (keyword.isEmpty()) {
            return Result.failure(PrayerTimesException(PrayerErrorKind.EMPTY_QUERY))
        }
        return try {
            val city = pickCity(api.findCities(keyword), keyword)
                ?: throw PrayerTimesException(PrayerErrorKind.CITY_NOT_FOUND)
            Result.success(api.schedule(city.id, date))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Result.failure(t as? PrayerTimesException ?: PrayerTimesException(kindOf(t), cause = t))
        }
    }

    private fun kindOf(t: Throwable): PrayerErrorKind = when (t) {
        is IOException -> PrayerErrorKind.NETWORK
        is SerializationException -> PrayerErrorKind.INVALID_RESPONSE
        else -> PrayerErrorKind.INVALID_RESPONSE
    }
}

/**
 * Pilih kota yang paling cocok: utamakan yang namanya memuat kata kunci, lalu hasil pertama —
 * API sudah mengurutkan hasil pencarian berdasarkan relevansi.
 */
internal fun pickCity(cities: List<PrayerCity>, keyword: String): PrayerCity? =
    cities.firstOrNull { it.name.contains(keyword, ignoreCase = true) } ?: cities.firstOrNull()
