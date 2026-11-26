package com.harimu.app.core.nusantara

/** Repository referensi statis: unduh sekali, lalu pencarian dilakukan di perangkat. */
class ReferenceRepository(
    private val api: ReferenceApi = HttpReferenceApi()
) {

    suspend fun heroes(): Result<List<Hero>> =
        NusantaraHttp.run(SERVICE) { api.heroes().sortedBy { it.name } }

    suspend fun volcanoes(): Result<List<Volcano>> =
        NusantaraHttp.run(SERVICE) { api.volcanoes().sortedBy { it.name } }

    companion object {
        private const val SERVICE = HttpReferenceApi.SERVICE
    }
}

internal fun List<Hero>.filterByQuery(query: String): List<Hero> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }
}

internal fun List<Volcano>.filterByQuery(query: String): List<Volcano> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.geolocation.contains(q, ignoreCase = true) }
}
