package com.harimu.app.core.nusantara

/** Repository prakiraan cuaca BMKG: satu kode adm4, satu prakiraan 3 hari. */
class WeatherRepository(
    private val api: WeatherApi = HttpWeatherApi()
) {

    suspend fun load(adm4: String): Result<WeatherForecast> {
        val code = adm4.trim()
        if (code.isEmpty()) {
            return Result.failure(
                NusantaraException(NusantaraErrorKind.INVALID_RESPONSE, SERVICE)
            )
        }
        return NusantaraHttp.run(SERVICE) { api.forecast(code) }
    }

    companion object {
        private const val SERVICE = HttpWeatherApi.SERVICE
    }
}
