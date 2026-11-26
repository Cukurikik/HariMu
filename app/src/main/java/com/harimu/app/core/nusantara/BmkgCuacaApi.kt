package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Lokasi adm4 yang dikenali BMKG (provinsi → kab/kota → kecamatan → desa). */
data class WeatherLocation(
    val adm4: String,
    val province: String,
    val city: String,
    val district: String,
    val village: String
)

/** Satu slot prakiraan 3 jam. */
data class WeatherSlot(
    val localTime: String,
    val temperatureC: Int,
    val humidityPercent: Int,
    val conditionId: String,
    val conditionEn: String,
    val windSpeed: String,
    val iconUrl: String
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
                    localTime = slot.localDateTime.trim(),
                    temperatureC = slot.temperature,
                    humidityPercent = slot.humidity,
                    conditionId = slot.conditionId.trim(),
                    conditionEn = slot.conditionEn.trim(),
                    windSpeed = slot.windSpeed.toString(),
                    iconUrl = slot.icon.trim()
                )
            }
        return WeatherForecast(location = location, slots = slots)
    }
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
    @SerialName("local_datetime") val localDateTime: String = "",
    @SerialName("t") val temperature: Int = 0,
    @SerialName("hu") val humidity: Int = 0,
    @SerialName("weather") val conditionId: String = "",
    @SerialName("weather_desc_en") val conditionEn: String = "",
    @SerialName("ws") val windSpeed: Double = 0.0,
    @SerialName("image") val icon: String = ""
)
