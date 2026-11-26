package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Satu kejadian gempa dari feed resmi BMKG. */
data class Earthquake(
    val magnitude: String,
    val depth: String,
    val region: String,
    val date: String,
    val time: String,
    val dateTime: String,
    val coordinates: String,
    val potential: String,
    val felt: String,
    val shakemap: String
)

/** Data source gempa BMKG (tanpa kunci). Dokumentasi: https://data.bmkg.go.id */
interface EarthquakeApi {
    suspend fun latest(): Earthquake
    suspend fun recent(): List<Earthquake>
}

class HttpEarthquakeApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : EarthquakeApi {

    override suspend fun latest(): Earthquake = BmkgGempaJson.parseLatest(
        NusantaraHttp.getText(url("autogempa.json"), SERVICE)
    )

    override suspend fun recent(): List<Earthquake> = BmkgGempaJson.parseRecent(
        NusantaraHttp.getText(url("gempaterkini.json"), SERVICE)
    )

    private fun url(file: String): String =
        NusantaraHttp.url(baseUrl, "DataMKG", "TEWS", file).toString()

    companion object {
        const val DEFAULT_BASE_URL = "https://data.bmkg.go.id"
        const val SERVICE = "BMKG"
    }
}

/** Pemetaan JSON BMKG; dipisah agar bisa diuji tanpa jaringan. */
object BmkgGempaJson {

    fun parseLatest(body: String): Earthquake {
        val envelope = decodeNusantara<LatestEnvelope>(body, HttpEarthquakeApi.SERVICE)
        return envelope.info.gempa.toDomain()
    }

    fun parseRecent(body: String): List<Earthquake> {
        val envelope = decodeNusantara<RecentEnvelope>(body, HttpEarthquakeApi.SERVICE)
        return envelope.info.gempa.mapNotNull { it.toDomainOrNull() }
    }

    private fun GempaDto.toDomain(): Earthquake = toDomainOrNull()
        ?: throw NusantaraException(
            NusantaraErrorKind.INVALID_RESPONSE,
            HttpEarthquakeApi.SERVICE
        )

    private fun GempaDto.toDomainOrNull(): Earthquake? {
        val magnitudeText = magnitude.orEmpty().trim()
        val regionText = region.orEmpty().trim()
        if (magnitudeText.isBlank() || regionText.isBlank()) return null
        return Earthquake(
            magnitude = magnitudeText,
            depth = depth.orEmpty().trim(),
            region = regionText,
            date = tanggal.orEmpty().trim(),
            time = jam.orEmpty().trim(),
            dateTime = dateTime.orEmpty().trim(),
            coordinates = coordinates.orEmpty().trim(),
            potential = potensi.orEmpty().trim(),
            felt = dirasakan.orEmpty().trim(),
            shakemap = shakemap.orEmpty().trim()
        )
    }
}

@Serializable
private data class LatestEnvelope(
    @SerialName("Infogempa") val info: LatestInfo = LatestInfo()
)

@Serializable
private data class LatestInfo(val gempa: GempaDto = GempaDto())

@Serializable
private data class RecentEnvelope(
    @SerialName("Infogempa") val info: RecentInfo = RecentInfo()
)

@Serializable
private data class RecentInfo(val gempa: List<GempaDto> = emptyList())

@Serializable
private data class GempaDto(
    @SerialName("Tanggal") val tanggal: String? = null,
    @SerialName("Jam") val jam: String? = null,
    @SerialName("DateTime") val dateTime: String? = null,
    @SerialName("Coordinates") val coordinates: String? = null,
    @SerialName("Magnitude") val magnitude: String? = null,
    @SerialName("Kedalaman") val depth: String? = null,
    @SerialName("Wilayah") val region: String? = null,
    @SerialName("Potensi") val potensi: String? = null,
    @SerialName("Dirasakan") val dirasakan: String? = null,
    @SerialName("Shakemap") val shakemap: String? = null
)
