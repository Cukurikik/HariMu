package com.harimu.app.core.nusantara

/** Repository sekolah: pencarian per nama dengan paging dari layanan. */
class SchoolRepository(
    private val api: SchoolApi = HttpSchoolApi()
) {

    suspend fun search(query: String, page: Int = 1): Result<SchoolPage> =
        NusantaraHttp.run(SERVICE) { api.search(query.trim(), page.coerceAtLeast(1)) }

    companion object {
        private const val SERVICE = HttpSchoolApi.SERVICE
    }
}
