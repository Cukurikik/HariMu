package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class PesantrenRegion(
    val id: String,
    val name: String
)

data class Pesantren(
    val name: String,
    val registrationNumber: String,
    val address: String,
    val cleric: String
)

/**
 * Data source data pesantren Kemenag (api-pesantren-indonesia, tanpa kunci).
 * Dokumentasi: https://github.com/nasrul21/data-pesantren-indonesia
 */
interface PesantrenApi {
    suspend fun provinces(): List<PesantrenRegion>
    suspend fun regencies(provinceId: String): List<PesantrenRegion>
    suspend fun pesantren(regencyId: String): List<Pesantren>
}

class HttpPesantrenApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : PesantrenApi {

    override suspend fun provinces(): List<PesantrenRegion> =
        PesantrenJson.parseRegions(
            NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "provinsi.json").toString(), SERVICE)
        )

    override suspend fun regencies(provinceId: String): List<PesantrenRegion> =
        PesantrenJson.parseRegions(
            NusantaraHttp.getText(
                NusantaraHttp.url(baseUrl, "kabupaten", "${provinceId.trim()}.json").toString(),
                SERVICE
            )
        )

    override suspend fun pesantren(regencyId: String): List<Pesantren> =
        PesantrenJson.parsePesantren(
            NusantaraHttp.getText(
                NusantaraHttp.url(baseUrl, "pesantren", "${regencyId.trim()}.json").toString(),
                SERVICE
            )
        )

    companion object {
        const val DEFAULT_BASE_URL = "https://api-pesantren-indonesia.vercel.app"
        const val SERVICE = "api-pesantren-indonesia"
    }
}

/** Pemetaan JSON pesantren; entri tanpa nama dibuang. */
object PesantrenJson {

    fun parseRegions(body: String): List<PesantrenRegion> =
        decodeNusantara<List<PesantrenRegionDto>>(body, HttpPesantrenApi.SERVICE)
            .mapNotNull { dto ->
                val id = dto.id.orEmpty().trim()
                val name = dto.name.orEmpty().trim()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                PesantrenRegion(id = id, name = name)
            }
            .sortedBy { it.name }

    fun parsePesantren(body: String): List<Pesantren> =
        decodeNusantara<List<PesantrenDto>>(body, HttpPesantrenApi.SERVICE)
            .mapNotNull { dto ->
                val name = dto.name.orEmpty().trim()
                if (name.isBlank()) return@mapNotNull null
                Pesantren(
                    name = name,
                    registrationNumber = dto.registrationNumber.orEmpty().trim(),
                    address = dto.address.orEmpty().trim(),
                    cleric = dto.cleric.orEmpty().trim()
                )
            }
            .sortedBy { it.name }
}

@Serializable
private data class PesantrenRegionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("nama") val name: String? = null
)

@Serializable
private data class PesantrenDto(
    @SerialName("nama") val name: String? = null,
    @SerialName("nspp") val registrationNumber: String? = null,
    @SerialName("alamat") val address: String? = null,
    @SerialName("kyai") val cleric: String? = null
)
