package com.harimu.app.core.nusantara

/** Repository doa harian: unduh sekali, pencarian dan pemilihan dilakukan di perangkat. */
class DoaRepository(
    private val api: DoaApi = HttpDoaApi()
) {

    suspend fun all(): Result<List<Doa>> = NusantaraHttp.run(SERVICE) { api.all() }

    companion object {
        private const val SERVICE = HttpDoaApi.SERVICE
    }
}

internal fun List<Doa>.filterByQuery(query: String): List<Doa> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.title.contains(q, ignoreCase = true) || it.meaning.contains(q, ignoreCase = true) }
}
