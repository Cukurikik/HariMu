package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Hero
import com.harimu.app.core.nusantara.ReferenceRepository
import com.harimu.app.core.nusantara.Volcano
import com.harimu.app.core.nusantara.filterByQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HeroesUiState(
    val heroes: List<Hero> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
) {
    val visible: List<Hero> get() = heroes.filterByQuery(query)
}

class HeroesViewModel(
    private val repository: ReferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HeroesUiState())
    val state: StateFlow<HeroesUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun load(isEnglish: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.heroes() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            heroes = it,
                            loaded = true,
                            error = null,
                            fetchedAtMillis = System.currentTimeMillis()
                        )
                    },
                    onFailure = { current.copy(isLoading = false, error = it.toNusantaraMessage(isEnglish)) }
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HeroesViewModel(container.references) as T
    }
}

data class VolcanoesUiState(
    val volcanoes: List<Volcano> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
) {
    val visible: List<Volcano> get() = volcanoes.filterByQuery(query)
}

class VolcanoesViewModel(
    private val repository: ReferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VolcanoesUiState())
    val state: StateFlow<VolcanoesUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun load(isEnglish: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.volcanoes() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            volcanoes = it,
                            loaded = true,
                            error = null,
                            fetchedAtMillis = System.currentTimeMillis()
                        )
                    },
                    onFailure = { current.copy(isLoading = false, error = it.toNusantaraMessage(isEnglish)) }
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VolcanoesViewModel(container.references) as T
    }
}
