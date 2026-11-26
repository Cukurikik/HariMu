package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Doa(
    val id: String,
    val title: String,
    val arabic: String,
    val latin: String,
    val meaning: String
)

/** Data source doa harian (doa-doa-api, tanpa kunci). */
interface DoaApi {
    suspend fun all(): List<Doa>
}

class HttpDoaApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : DoaApi {

    override suspend fun all(): List<Doa> = DoaJson.parse(
        NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "api").toString(), SERVICE)
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://doa-doa-api-ahmadramadhan.fly.dev"
        const val SERVICE = "doa-doa"
    }
}

/** Pemetaan JSON doa; dipisah agar bisa diuji tanpa jaringan. */
object DoaJson {

    fun parse(body: String): List<Doa> =
        decodeNusantara<List<DoaDto>>(body, HttpDoaApi.SERVICE)
            .mapNotNull { dto ->
                val title = dto.title.orEmpty().trim()
                if (title.isBlank()) return@mapNotNull null
                Doa(
                    id = dto.id.orEmpty().trim(),
                    title = title,
                    arabic = dto.arabic.orEmpty().trim(),
                    latin = dto.latin.orEmpty().trim(),
                    meaning = dto.meaning.orEmpty().trim()
                )
            }
}

@Serializable
private data class DoaDto(
    @SerialName("id") val id: String? = null,
    @SerialName("doa") val title: String? = null,
    @SerialName("ayat") val arabic: String? = null,
    @SerialName("latin") val latin: String? = null,
    @SerialName("artinya") val meaning: String? = null
)
