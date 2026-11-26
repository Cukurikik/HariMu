package com.harimu.app.core.nusantara

import kotlinx.serialization.Serializable

/** Satu wilayah administratif (provinsi, kabupaten/kota, kecamatan, atau desa/kelurahan). */
data class Region(val id: String, val name: String)

/**
 * Data source wilayah Indonesia (emsifa, statis tanpa kunci).
 * Dokumentasi: https://github.com/emsifa/api-wilayah-indonesia
 */
interface RegionApi {
    suspend fun provinces(): List<Region>
    suspend fun regencies(provinceId: String): List<Region>
    suspend fun districts(regencyId: String): List<Region>
    suspend fun villages(districtId: String): List<Region>
}

class HttpRegionApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : RegionApi {

    override suspend fun provinces(): List<Region> = fetch("provinces.json")

    override suspend fun regencies(provinceId: String): List<Region> = fetch("regencies/$provinceId.json")

    override suspend fun districts(regencyId: String): List<Region> = fetch("districts/$regencyId.json")

    override suspend fun villages(districtId: String): List<Region> = fetch("villages/$districtId.json")

    private suspend fun fetch(path: String): List<Region> = WilayahJson.parse(
        NusantaraHttp.getText(
            NusantaraHttp.url(baseUrl, "api-wilayah-indonesia", "api", path).toString(),
            SERVICE
        )
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://www.emsifa.com"
        const val SERVICE = "emsifa"
    }
}

/** Pemetaan JSON wilayah; id bersifat hierarkis sehingga bisa dipakai sebagai `adm4` BMKG. */
object WilayahJson {

    fun parse(body: String): List<Region> =
        decodeNusantara<List<RegionDto>>(body, HttpRegionApi.SERVICE)
            .mapNotNull { dto ->
                val id = dto.id.trim()
                val name = dto.name.trim()
                if (id.isBlank() || name.isBlank()) null else Region(id, name)
            }
}

@Serializable
private data class RegionDto(
    val id: String = "",
    val name: String = ""
)
