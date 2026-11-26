package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SundaneseEntry(
    val sorangan: String,
    val batur: String,
    val loma: String,
    val indonesian: String,
    val english: String
) {
    val isEmpty: Boolean
        get() = sorangan.isBlank() && batur.isBlank() && loma.isBlank() &&
            indonesian.isBlank() && english.isBlank()
}

/**
 * Data source undak usuk basa Sunda (hibersunda, tanpa kunci).
 * Dokumentasi: https://github.com/hiberin/hibersunda
 */
interface SundaneseApi {
    suspend fun lookup(word: String): List<SundaneseEntry>
}

class HttpSundaneseApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : SundaneseApi {

    override suspend fun lookup(word: String): List<SundaneseEntry> {
        val clean = word.trim().lowercase().replace("/", " ")
        if (clean.isBlank()) return emptyList()
        return SundaneseJson.parse(
            NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "undakusukbasa", clean).toString(), SERVICE)
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://hibersunda.vercel.app"
        const val SERVICE = "hibersunda"
    }
}

/** Pemetaan JSON kamus Sunda; entri kosong dibuang agar daftar tetap bersih. */
object SundaneseJson {

    fun parse(body: String): List<SundaneseEntry> =
        decodeNusantara<List<SundaneseDto>>(body, HttpSundaneseApi.SERVICE)
            .map { dto ->
                SundaneseEntry(
                    sorangan = dto.sorangan.orEmpty().trim(),
                    batur = dto.batur.orEmpty().trim(),
                    loma = dto.loma.orEmpty().trim(),
                    indonesian = dto.indonesian.orEmpty().trim(),
                    english = dto.english.orEmpty().trim()
                )
            }
            .filterNot { it.isEmpty }
}

@Serializable
private data class SundaneseDto(
    @SerialName("sorangan") val sorangan: String? = null,
    @SerialName("batur") val batur: String? = null,
    @SerialName("loma") val loma: String? = null,
    @SerialName("bindo") val indonesian: String? = null,
    @SerialName("english") val english: String? = null
)
