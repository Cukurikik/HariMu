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
        if (magnitude.isBlank() || region.isBlank()) return null
        return Earthquake(
            magnitude = magnitude.trim(),
            depth = depth.trim(),
            region = region.trim(),
            date = tanggal.trim(),
            time = jam.trim(),
            dateTime = dateTime.trim(),
            coordinates = coordinates.trim(),
            potential = potensi.trim(),
            felt = dirasakan.trim(),
            shakemap = shakemap.trim()
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
    @SerialName("Tanggal") val tanggal: String = "",
    @SerialName("Jam") val jam: String = "",
    @SerialName("DateTime") val dateTime: String = "",
    @SerialName("Coordinates") val coordinates: String = "",
    @SerialName("Magnitude") val magnitude: String = "",
    @SerialName("Kedalaman") val depth: String = "",
    @SerialName("Wilayah") val region: String = "",
    @SerialName("Potensi") val potensi: String = "",
    @SerialName("Dirasakan") val dirasakan: String = "",
    @SerialName("Shakemap") val shakemap: String = ""
)
