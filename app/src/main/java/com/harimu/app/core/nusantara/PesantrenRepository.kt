package com.harimu.app.core.nusantara

/** Repository pesantren: provinsi → kabupaten/kota → daftar pesantren. */
class PesantrenRepository(
    private val api: PesantrenApi = HttpPesantrenApi()
) {

    suspend fun provinces(): Result<List<PesantrenRegion>> =
        NusantaraHttp.run(SERVICE) { api.provinces() }

    suspend fun regencies(provinceId: String): Result<List<PesantrenRegion>> =
        NusantaraHttp.run(SERVICE) { api.regencies(provinceId) }

    suspend fun pesantren(regencyId: String): Result<List<Pesantren>> =
        NusantaraHttp.run(SERVICE) { api.pesantren(regencyId) }

    companion object {
        private const val SERVICE = HttpPesantrenApi.SERVICE
    }
}

internal fun List<Pesantren>.filterPesantren(query: String): List<Pesantren> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter {
        it.name.contains(q, ignoreCase = true) ||
            it.address.contains(q, ignoreCase = true) ||
            it.cleric.contains(q, ignoreCase = true)
    }
}
