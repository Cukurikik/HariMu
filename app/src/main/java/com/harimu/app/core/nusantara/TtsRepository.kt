package com.harimu.app.core.nusantara

/**
 * Repository kunci TTS. Layanan ini mengembalikan 200 walau tidak ada hasil,
 * jadi daftar kosong diperlakukan sebagai "tidak ditemukan", bukan kegagalan.
 */
class TtsRepository(
    private val api: TtsApi = HttpTtsApi()
) {

    suspend fun answers(clue: String): Result<TtsResult> =
        NusantaraHttp.run(SERVICE) { api.answers(clue) }

    companion object {
        private const val SERVICE = HttpTtsApi.SERVICE
    }
}
