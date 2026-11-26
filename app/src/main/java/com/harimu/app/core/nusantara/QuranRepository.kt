package com.harimu.app.core.nusantara

/** Repository Al-Qur'an: daftar surah dan rincian ayat per surah. */
class QuranRepository(
    private val api: QuranApi = HttpQuranApi()
) {

    suspend fun surahs(): Result<List<QuranSurah>> =
        NusantaraHttp.run(SERVICE) { api.surahs() }

    suspend fun surah(number: Int): Result<QuranSurahDetail> =
        NusantaraHttp.run(SERVICE) { api.surah(number) }

    companion object {
        private const val SERVICE = HttpQuranApi.SERVICE
    }
}

internal fun List<QuranSurah>.filterSurahs(query: String): List<QuranSurah> {
    val q = query.trim()
    if (q.isEmpty()) return this
    val asNumber = q.toIntOrNull()
    if (asNumber != null) return filter { it.number == asNumber }
    return filter {
        it.name.contains(q, ignoreCase = true) ||
            it.translationId.contains(q, ignoreCase = true) ||
            it.translationEn.contains(q, ignoreCase = true)
    }
}
