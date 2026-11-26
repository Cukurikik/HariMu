package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

data class TtsAnswer(
    val word: String,
    val clue: String,
    val stars: Int
)

data class TtsResult(
    val title: String,
    val total: Int,
    val answers: List<TtsAnswer>
)

/** Data source kunci jawaban TTS (kunci-tts-api, tanpa kunci). */
interface TtsApi {
    suspend fun answers(clue: String): TtsResult
}

class HttpTtsApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : TtsApi {

    override suspend fun answers(clue: String): TtsResult {
        val url = NusantaraHttp.url(baseUrl, "api", "answers").newBuilder()
            .addQueryParameter("question", clue.trim())
            .build()
        return TtsJson.parse(NusantaraHttp.getText(url.toString(), SERVICE))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://kunci-tts-api.vercel.app"
        const val SERVICE = "kunci-tts-api"
    }
}

/** Pemetaan JSON kunci TTS; bintang dibaca toleran karena bisa berupa angka atau teks. */
object TtsJson {

    fun parse(body: String): TtsResult {
        val dto = decodeNusantara<TtsDto>(body, HttpTtsApi.SERVICE)
        val answers = dto.answers.orEmpty().mapNotNull { item ->
            val word = item.word.orEmpty().trim()
            if (word.isBlank()) return@mapNotNull null
            TtsAnswer(
                word = word,
                clue = item.clue.orEmpty().trim(),
                stars = item.stars?.intOrNull ?: 0
            )
        }
        return TtsResult(
            title = dto.title.orEmpty().trim(),
            total = dto.total?.intOrNull ?: answers.size,
            answers = answers
        )
    }
}

@Serializable
private data class TtsDto(
    @SerialName("title") val title: String? = null,
    @SerialName("total") val total: JsonPrimitive? = null,
    @SerialName("answers") val answers: List<TtsAnswerDto>? = null
)

@Serializable
private data class TtsAnswerDto(
    @SerialName("word") val word: String? = null,
    @SerialName("clue") val clue: String? = null,
    @SerialName("stars") val stars: JsonPrimitive? = null
)
