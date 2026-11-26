package com.harimu.app.core.nusantara

/** Repository kamus Sunda: satu kata per permintaan, hasil bisa berisi beberapa arti. */
class SundaneseRepository(
    private val api: SundaneseApi = HttpSundaneseApi()
) {

    suspend fun lookup(word: String): Result<List<SundaneseEntry>> =
        NusantaraHttp.run(SERVICE) { api.lookup(word) }

    companion object {
        private const val SERVICE = HttpSundaneseApi.SERVICE
    }
}
