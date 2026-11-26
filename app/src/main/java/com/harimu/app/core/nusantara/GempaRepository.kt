package com.harimu.app.core.nusantara

import kotlinx.coroutines.CancellationException

data class EarthquakeSnapshot(
    val latest: Earthquake,
    val recent: List<Earthquake>
)

/**
 * Repository gempa BMKG: satu kali muat mengambil gempa terbaru dan daftar gempa terkini.
 * Bila daftar gagal dimuat tetapi gempa terbaru berhasil, layar tetap terisi data terbaru.
 */
class EarthquakeRepository(
    private val api: EarthquakeApi = HttpEarthquakeApi()
) {

    suspend fun load(): Result<EarthquakeSnapshot> = NusantaraHttp.run(SERVICE) {
        val latest = api.latest()
        val recent = try {
            api.recent()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }
        EarthquakeSnapshot(latest = latest, recent = recent.ifEmpty { listOf(latest) })
    }

    companion object {
        private const val SERVICE = HttpEarthquakeApi.SERVICE
    }
}
