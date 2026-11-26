package com.harimu.app.core.nusantara

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class FootballLeague(
    val id: String,
    val name: String,
    val abbreviation: String
)

data class StandingRow(
    val rank: Int,
    val team: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalDifference: String,
    val points: Int
)

data class FootballStandings(
    val league: String,
    val season: String,
    val rows: List<StandingRow>
)

/**
 * Data source klasemen sepak bola (football-standings-api, tanpa kunci, sumber ESPN).
 * Dokumentasi: https://github.com/azharimm/football-standings-api
 */
interface FootballApi {
    suspend fun leagues(): List<FootballLeague>
    suspend fun standings(leagueId: String, season: Int?): FootballStandings
}

class HttpFootballApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : FootballApi {

    override suspend fun leagues(): List<FootballLeague> = FootballJson.parseLeagues(
        NusantaraHttp.getText(NusantaraHttp.url(baseUrl, "leagues").toString(), SERVICE)
    )

    override suspend fun standings(leagueId: String, season: Int?): FootballStandings {
        val url = NusantaraHttp.url(baseUrl, "leagues", leagueId, "standings").newBuilder()
            .apply { season?.let { addQueryParameter("season", it.toString()) } }
            .build()
        return FootballJson.parseStandings(NusantaraHttp.getText(url.toString(), SERVICE))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://football-standings-api-azharimm.vercel.app"
        const val SERVICE = "football-standings"
    }
}

/** Pemetaan JSON klasemen: statistik dibaca dari daftar `stats` berdasarkan nama. */
object FootballJson {

    fun parseLeagues(body: String): List<FootballLeague> =
        decodeNusantara<LeagueListDto>(body, HttpFootballApi.SERVICE)
            .data.orEmpty()
            .mapNotNull { dto ->
                val id = dto.id.orEmpty().trim()
                val name = dto.name.orEmpty().trim()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                FootballLeague(id = id, name = name, abbreviation = dto.abbreviation.orEmpty().trim())
            }

    fun parseStandings(body: String): FootballStandings {
        val dto = decodeNusantara<StandingsDto>(body, HttpFootballApi.SERVICE)
        val data = dto.data ?: throw NusantaraException(
            NusantaraErrorKind.INVALID_RESPONSE,
            HttpFootballApi.SERVICE
        )
        val rows = data.standings.orEmpty().mapIndexedNotNull { index, row ->
            val team = row.team?.name.orEmpty().trim().ifBlank { row.team?.displayName.orEmpty().trim() }
            if (team.isBlank()) return@mapIndexedNotNull null
            val stats = row.stats.orEmpty().associate { it.name.orEmpty() to it.displayValue.orEmpty() }
            StandingRow(
                rank = stats["rank"]?.toIntOrNull() ?: (index + 1),
                team = team,
                played = stats["gamesPlayed"]?.toIntOrNull() ?: 0,
                wins = stats["wins"]?.toIntOrNull() ?: 0,
                draws = stats["ties"]?.toIntOrNull() ?: 0,
                losses = stats["losses"]?.toIntOrNull() ?: 0,
                goalDifference = stats["pointDifferential"].orEmpty(),
                points = stats["points"]?.toIntOrNull() ?: 0
            )
        }
        return FootballStandings(
            league = data.name.orEmpty().trim(),
            season = data.seasonDisplay.orEmpty().trim(),
            rows = rows
        )
    }
}

@Serializable
private data class LeagueListDto(
    @SerialName("data") val data: List<LeagueDto>? = null
)

@Serializable
private data class LeagueDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("abbr") val abbreviation: String? = null
)

@Serializable
private data class StandingsDto(
    @SerialName("data") val data: StandingsDataDto? = null
)

@Serializable
private data class StandingsDataDto(
    @SerialName("name") val name: String? = null,
    @SerialName("seasonDisplay") val seasonDisplay: String? = null,
    @SerialName("standings") val standings: List<StandingDto>? = null
)

@Serializable
private data class StandingDto(
    @SerialName("team") val team: TeamDto? = null,
    @SerialName("stats") val stats: List<StatDto>? = null
)

@Serializable
private data class TeamDto(
    @SerialName("name") val name: String? = null,
    @SerialName("displayName") val displayName: String? = null
)

@Serializable
private data class StatDto(
    @SerialName("name") val name: String? = null,
    @SerialName("displayValue") val displayValue: String? = null
)
