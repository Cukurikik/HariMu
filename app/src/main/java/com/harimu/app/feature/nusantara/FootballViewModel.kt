package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.FootballLeague
import com.harimu.app.core.nusantara.FootballRepository
import com.harimu.app.core.nusantara.FootballStandings
import com.harimu.app.core.nusantara.filterLeagues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FootballUiState(
    val leagues: List<FootballLeague> = emptyList(),
    val query: String = "",
    val selected: FootballLeague? = null,
    val season: Int = DEFAULT_SEASON,
    val standings: FootballStandings? = null,
    val isLoading: Boolean = false,
    val isLoadingStandings: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val fetchedAtMillis: Long? = null,
    val standingsFetchedAtMillis: Long? = null
) {
    val visibleLeagues: List<FootballLeague> get() = leagues.filterLeagues(query)

    companion object {
        const val DEFAULT_SEASON = 2026
        val SEASONS = listOf(2026, 2025, 2024)
    }
}

class FootballViewModel(
    private val repository: FootballRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FootballUiState())
    val state: StateFlow<FootballUiState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var standingsJob: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun loadLeagues(isEnglish: Boolean) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.leagues() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            leagues = it,
                            loaded = true,
                            error = null,
                            fetchedAtMillis = System.currentTimeMillis()
                        )
                    },
                    onFailure = {
                        current.copy(isLoading = false, error = it.toNusantaraMessage(isEnglish))
                    }
                )
            }
        }
    }

    fun selectLeague(league: FootballLeague, isEnglish: Boolean) {
        _state.update { it.copy(selected = league, standings = null, error = null) }
        loadStandings(isEnglish)
    }

    fun selectSeason(season: Int, isEnglish: Boolean) {
        if (_state.value.season == season) return
        _state.update { it.copy(season = season) }
        loadStandings(isEnglish)
    }

    fun closeLeague() {
        standingsJob?.cancel()
        _state.update { it.copy(selected = null, standings = null, isLoadingStandings = false, error = null) }
    }

    fun loadStandings(isEnglish: Boolean) {
        val current = _state.value
        val league = current.selected ?: return
        standingsJob?.cancel()
        standingsJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingStandings = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                repository.standings(league.id, current.season)
            }
            _state.update { state ->
                result.fold(
                    onSuccess = {
                        state.copy(
                            isLoadingStandings = false,
                            standings = it,
                            error = null,
                            standingsFetchedAtMillis = System.currentTimeMillis()
                        )
                    },
                    onFailure = {
                        state.copy(isLoadingStandings = false, error = it.toNusantaraMessage(isEnglish))
                    }
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FootballViewModel(container.football) as T
    }
}
