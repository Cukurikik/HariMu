package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class School(
    val npsn: String,
    val name: String,
    val form: String,
    val status: String,
    val address: String,
    val province: String,
    val regency: String,
    val district: String,
    val latitude: String,
    val longitude: String
)

data class SchoolPage(
    val schools: List<School>,
    val page: Int,
    val perPage: Int,
    val total: Int
) {
    val hasMore: Boolean get() = schools.isNotEmpty() && page * perPage < total
}

/** Data source daftar sekolah Indonesia (api-sekolah-indonesia, tanpa kunci). */
interface SchoolApi {
    suspend fun search(query: String, page: Int): SchoolPage
}

class HttpSchoolApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : SchoolApi {

    override suspend fun search(query: String, page: Int): SchoolPage {
        val url = NusantaraHttp.url(baseUrl, "sekolah").newBuilder()
            .addQueryParameter("nama", query.trim())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .build()
        return SchoolJson.parse(NusantaraHttp.getText(url.toString(), SERVICE))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api-sekolah-indonesia.vercel.app"
        const val SERVICE = "api-sekolah-indonesia"
    }
}

/** Pemetaan JSON sekolah; dipisah agar bisa diuji tanpa jaringan. */
object SchoolJson {

    fun parse(body: String): SchoolPage {
        val dto = decodeNusantara<SchoolPageDto>(body, HttpSchoolApi.SERVICE)
        val schools = dto.schools.orEmpty().mapNotNull { item ->
            val name = item.name.orEmpty().trim()
            if (name.isBlank()) return@mapNotNull null
            School(
                npsn = item.npsn.orEmpty().trim(),
                name = name,
                form = item.form.orEmpty().trim(),
                status = item.status.orEmpty().trim(),
                address = item.address.orEmpty().trim(),
                province = item.province.orEmpty().trim(),
                regency = item.regency.orEmpty().trim(),
                district = item.district.orEmpty().trim(),
                latitude = item.latitude.orEmpty().trim(),
                longitude = item.longitude.orEmpty().trim()
            )
        }
        val page = dto.page?.intOrNull ?: 1
        val perPage = (dto.perPage?.intOrNull ?: schools.size).coerceAtLeast(1)
        val total = dto.total?.intOrNull ?: schools.size
        return SchoolPage(schools = schools, page = page, perPage = perPage, total = total)
    }
}

@Serializable
private data class SchoolPageDto(
    @SerialName("dataSekolah") val schools: List<SchoolDto>? = null,
    @SerialName("total_data") val total: JsonPrimitive? = null,
    @SerialName("page") val page: JsonPrimitive? = null,
    @SerialName("per_page") val perPage: JsonPrimitive? = null
)

@Serializable
private data class SchoolDto(
    @SerialName("npsn") val npsn: String? = null,
    @SerialName("sekolah") val name: String? = null,
    @SerialName("bentuk") val form: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("alamat_jalan") val address: String? = null,
    @SerialName("propinsi") val province: String? = null,
    @SerialName("kabupaten_kota") val regency: String? = null,
    @SerialName("kecamatan") val district: String? = null,
    @SerialName("lintang") val latitude: String? = null,
    @SerialName("bujur") val longitude: String? = null
)
