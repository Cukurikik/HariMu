package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Lokasi adm4 yang dikenali BMKG (provinsi → kab/kota → kecamatan → desa). */
data class WeatherLocation(
    val adm4: String,
    val province: String,
    val city: String,
    val district: String,
    val village: String
)

/**
 * Satu slot prakiraan 3 jam. [temperatureC] dan [humidityPercent] bernilai null bila BMKG tidak
 * mengirim angka untuk slot itu; UI menampilkan tanda kosong, bukan 0 °C/0 %.
 */
data class WeatherSlot(
    val localTime: String,
    val temperatureC: Int?,
    val humidityPercent: Int?,
    val conditionId: String,
    val conditionEn: String,
    val windSpeed: String,
    val iconUrl: String,
    val conditionDescId: String = ""
)

data class WeatherForecast(
    val location: WeatherLocation,
    val slots: List<WeatherSlot>
)

/** Data source prakiraan cuaca resmi BMKG (tanpa kunci). */
interface WeatherApi {
    suspend fun forecast(adm4: String): WeatherForecast
}

class HttpWeatherApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : WeatherApi {

    override suspend fun forecast(adm4: String): WeatherForecast {
        val url = NusantaraHttp.url(baseUrl, "publik", "prakiraan-cuaca")
            .newBuilder()
            .addQueryParameter("adm4", adm4.trim())
            .build()
        return BmkgCuacaJson.parse(NusantaraHttp.getText(url.toString(), SERVICE))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.bmkg.go.id"
        const val SERVICE = "BMKG"
    }
}

/** Pemetaan JSON prakiraan cuaca BMKG; dipisah agar bisa diuji tanpa jaringan. */
object BmkgCuacaJson {

    fun parse(body: String): WeatherForecast {
        val response = decodeNusantara<ForecastResponse>(body, HttpWeatherApi.SERVICE)
        val location = WeatherLocation(
            adm4 = response.lokasi.adm4.trim(),
            province = response.lokasi.provinsi.trim(),
            city = response.lokasi.kotkab.trim(),
            district = response.lokasi.kecamatan.trim(),
            village = response.lokasi.desa.trim()
        )
        if (location.adm4.isBlank()) {
            throw NusantaraException(NusantaraErrorKind.INVALID_RESPONSE, HttpWeatherApi.SERVICE)
        }
        val slots = response.data
            .flatMap { block -> block.cuaca.flatten() }
            .map { slot ->
                WeatherSlot(
                    localTime = slot.localDateTime.orEmpty().trim(),
                    temperatureC = slot.temperature?.intOrNull,
                    humidityPercent = slot.humidity?.intOrNull,
                    conditionId = slot.conditionCode.text(),
                    conditionEn = slot.conditionEn.text(),
                    windSpeed = slot.windSpeed.text(),
                    iconUrl = slot.icon.text(),
                    conditionDescId = slot.conditionDescId.text()
                )
            }
        return WeatherForecast(location = location, slots = slots)
    }

    private fun JsonPrimitive?.text(): String = this?.contentOrNull?.trim().orEmpty()
}

@Serializable
private data class ForecastResponse(
    val lokasi: LocationDto = LocationDto(),
    val data: List<ForecastBlock> = emptyList()
)

@Serializable
private data class ForecastBlock(
    val cuaca: List<List<SlotDto>> = emptyList()
)

@Serializable
private data class LocationDto(
    val adm4: String = "",
    val provinsi: String = "",
    val kotkab: String = "",
    val kecamatan: String = "",
    val desa: String = ""
)

@Serializable
private data class SlotDto(
    // BMKG mengirim angka untuk t/hu/weather/ws dan null untuk sebagian field, jadi semuanya
    // dibaca sebagai JsonPrimitive agar decoding tidak gagal.
    @SerialName("local_datetime") val localDateTime: String? = null,
    @SerialName("t") val temperature: JsonPrimitive? = null,
    @SerialName("hu") val humidity: JsonPrimitive? = null,
    @SerialName("weather") val conditionCode: JsonPrimitive? = null,
    @SerialName("weather_desc") val conditionDescId: JsonPrimitive? = null,
    @SerialName("weather_desc_en") val conditionEn: JsonPrimitive? = null,
    @SerialName("ws") val windSpeed: JsonPrimitive? = null,
    @SerialName("image") val icon: JsonPrimitive? = null
)
