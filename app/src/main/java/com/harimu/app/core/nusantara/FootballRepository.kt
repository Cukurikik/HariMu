package com.harimu.app.core.nusantara

/** Repository klasemen sepak bola; daftar liga diurutkan agar mudah dicari. */
class FootballRepository(
    private val api: FootballApi = HttpFootballApi()
) {

    suspend fun leagues(): Result<List<FootballLeague>> =
        NusantaraHttp.run(SERVICE) { api.leagues().sortedBy { it.name } }

    suspend fun standings(leagueId: String, season: Int? = null): Result<FootballStandings> =
        NusantaraHttp.run(SERVICE) { api.standings(leagueId.trim(), season) }

    companion object {
        private const val SERVICE = HttpFootballApi.SERVICE
    }
}

internal fun List<FootballLeague>.filterLeagues(query: String): List<FootballLeague> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.abbreviation.contains(q, ignoreCase = true) }
}
