package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class QuranSurah(
    val number: Int,
    val name: String,
    val translationId: String,
    val translationEn: String,
    val verseCount: Int,
    val revelationId: String
)

data class QuranVerse(
    val number: Int,
    val arabic: String,
    val latin: String,
    val translationId: String,
    val translationEn: String,
    val audioUrl: String
)

data class QuranSurahDetail(
    val surah: QuranSurah,
    val verses: List<QuranVerse>
)

/**
 * Data source Al-Qur'an (Quran API ID, tanpa kunci).
 * Dokumentasi: https://github.com/renomureza/quran-api-id
 */
interface QuranApi {
    suspend fun surahs(): List<QuranSurah>
    suspend fun surah(number: Int): QuranSurahDetail
}

class HttpQuranApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : QuranApi {

    override suspend fun surahs(): List<QuranSurah> = QuranJson.parseSurahs(
        NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "surah").toString(), SERVICE)
    )

    override suspend fun surah(number: Int): QuranSurahDetail = QuranJson.parseSurah(
        NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "surah", number.toString()).toString(), SERVICE)
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://quran-api-id.vercel.app"
        const val SERVICE = "Quran API ID"
    }
}

/** Pemetaan JSON Al-Qur'an, dipisah agar bisa diuji tanpa jaringan. */
object QuranJson {

    fun parseSurahs(body: String): List<QuranSurah> =
        decodeNusantara<SurahListDto>(body, HttpQuranApi.SERVICE)
            .data.orEmpty()
            .mapNotNull { dto -> toSurah(dto.number, dto.verseCount, dto.name, dto.revelation) }
            .sortedBy { it.number }

    fun parseSurah(body: String): QuranSurahDetail {
        val data = decodeNusantara<SurahDetailDto>(body, HttpQuranApi.SERVICE).data
            ?: throw NusantaraException(NusantaraErrorKind.INVALID_RESPONSE, HttpQuranApi.SERVICE)
        val surah = toSurah(data.number, data.verseCount, data.name, data.revelation)
            ?: throw NusantaraException(NusantaraErrorKind.INVALID_RESPONSE, HttpQuranApi.SERVICE)
        val verses = data.verses.orEmpty().mapNotNull { verse ->
            val arabic = verse.text?.arabic.orEmpty().trim()
            if (arabic.isBlank()) return@mapNotNull null
            QuranVerse(
                number = verse.number?.inSurah ?: 0,
                arabic = arabic,
                latin = verse.text?.latin.orEmpty().trim(),
                translationId = verse.translation?.indonesian.orEmpty().trim(),
                translationEn = verse.translation?.english.orEmpty().trim(),
                audioUrl = verse.audio?.primary.orEmpty().trim()
            )
        }
        return QuranSurahDetail(surah = surah, verses = verses)
    }

    private fun toSurah(
        number: Int?,
        verseCount: Int?,
        name: SurahNameDto?,
        revelation: RevelationDto?
    ): QuranSurah? {
        val value = number ?: return null
        if (value <= 0) return null
        return QuranSurah(
            number = value,
            name = name?.latin.orEmpty().trim().ifBlank { "Surah $value" },
            translationId = name?.translation?.indonesian.orEmpty().trim(),
            translationEn = name?.translation?.english.orEmpty().trim(),
            verseCount = verseCount ?: 0,
            revelationId = revelation?.indonesian.orEmpty().trim()
        )
    }
}

@Serializable
private data class SurahListDto(
    @SerialName("data") val data: List<SurahDto>? = null
)

@Serializable
private data class SurahDetailDto(
    @SerialName("data") val data: SurahDetailDataDto? = null
)

@Serializable
private data class SurahDto(
    @SerialName("number") val number: Int? = null,
    @SerialName("numberOfVerses") val verseCount: Int? = null,
    @SerialName("name") val name: SurahNameDto? = null,
    @SerialName("revelation") val revelation: RevelationDto? = null
)

@Serializable
private data class SurahDetailDataDto(
    @SerialName("number") val number: Int? = null,
    @SerialName("numberOfVerses") val verseCount: Int? = null,
    @SerialName("name") val name: SurahNameDto? = null,
    @SerialName("revelation") val revelation: RevelationDto? = null,
    @SerialName("verses") val verses: List<VerseDto>? = null
)

@Serializable
private data class SurahNameDto(
    @SerialName("transliteration") val latin: String? = null,
    @SerialName("translation") val translation: TranslationPairDto? = null
)

@Serializable
private data class TranslationPairDto(
    @SerialName("id") val indonesian: String? = null,
    @SerialName("en") val english: String? = null
)

@Serializable
private data class RevelationDto(
    @SerialName("id") val indonesian: String? = null
)

@Serializable
private data class VerseDto(
    @SerialName("number") val number: VerseNumberDto? = null,
    @SerialName("text") val text: VerseTextDto? = null,
    @SerialName("translation") val translation: TranslationPairDto? = null,
    @SerialName("audio") val audio: VerseAudioDto? = null
)

@Serializable
private data class VerseNumberDto(
    @SerialName("inSurah") val inSurah: Int? = null
)

@Serializable
private data class VerseTextDto(
    @SerialName("arab") val arabic: String? = null,
    @SerialName("transliteration") val transliteration: LatinDto? = null
) {
    val latin: String? get() = transliteration?.english
}

@Serializable
private data class LatinDto(
    @SerialName("en") val english: String? = null
)

@Serializable
private data class VerseAudioDto(
    @SerialName("primary") val primary: String? = null
)
