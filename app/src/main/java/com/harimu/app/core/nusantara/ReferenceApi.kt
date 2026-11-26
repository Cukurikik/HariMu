package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class Hero(
    val name: String,
    val birthYear: String,
    val deathYear: String,
    val description: String,
    val ascensionYear: String
)

data class Volcano(
    val name: String,
    val form: String,
    val height: String,
    val lastEruption: String,
    val geolocation: String
)

/**
 * Data source referensi statis Indonesia (yogski/indonesia-public-static-api, tanpa kunci).
 * Dokumentasi: https://github.com/yogski/indonesia-public-static-api
 */
interface ReferenceApi {
    suspend fun heroes(): List<Hero>
    suspend fun volcanoes(): List<Volcano>
}

class HttpReferenceApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : ReferenceApi {

    override suspend fun heroes(): List<Hero> = ReferenceJson.parseHeroes(
        NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "api", "heroes").toString(), SERVICE)
    )

    override suspend fun volcanoes(): List<Volcano> = ReferenceJson.parseVolcanoes(
        NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "api", "volcanoes").toString(), SERVICE)
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://indonesia-public-static-api.vercel.app"
        const val SERVICE = "data statis Indonesia"
    }
}

/** Pemetaan JSON referensi; tahun bisa berupa angka atau teks, jadi dibaca sebagai primitif. */
object ReferenceJson {

    fun parseHeroes(body: String): List<Hero> =
        decodeNusantara<List<HeroDto>>(body, HttpReferenceApi.SERVICE)
            .mapNotNull { dto ->
                val name = dto.name.orEmpty().trim()
                if (name.isBlank()) return@mapNotNull null
                Hero(
                    name = name,
                    birthYear = dto.birthYear.text(),
                    deathYear = dto.deathYear.text(),
                    description = dto.description.orEmpty().trim(),
                    ascensionYear = dto.ascensionYear.text()
                )
            }

    fun parseVolcanoes(body: String): List<Volcano> =
        decodeNusantara<List<VolcanoDto>>(body, HttpReferenceApi.SERVICE)
            .mapNotNull { dto ->
                val name = dto.name.orEmpty().trim()
                if (name.isBlank()) return@mapNotNull null
                Volcano(
                    name = name,
                    form = dto.form.orEmpty().trim(),
                    height = dto.height.orEmpty().trim(),
                    lastEruption = dto.lastEruption.text(),
                    geolocation = dto.geolocation.orEmpty().trim()
                )
            }

    private fun JsonPrimitive?.text(): String = this?.contentOrNull?.trim().orEmpty()
}

@Serializable
private data class HeroDto(
    @SerialName("name") val name: String? = null,
    @SerialName("birth_year") val birthYear: JsonPrimitive? = null,
    @SerialName("death_year") val deathYear: JsonPrimitive? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("ascension_year") val ascensionYear: JsonPrimitive? = null
)

@Serializable
private data class VolcanoDto(
    @SerialName("nama") val name: String? = null,
    @SerialName("bentuk") val form: String? = null,
    @SerialName("tinggi_meter") val height: String? = null,
    @SerialName("estimasi_letusan_terakhir") val lastEruption: JsonPrimitive? = null,
    @SerialName("geolokasi") val geolocation: String? = null
)
