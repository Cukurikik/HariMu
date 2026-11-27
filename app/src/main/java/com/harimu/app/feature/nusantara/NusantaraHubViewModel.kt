package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Earthquake
import com.harimu.app.core.nusantara.EarthquakeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NusantaraHubUiState(
    val isLoading: Boolean = false,
    val latest: Earthquake? = null,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
)

class NusantaraHubViewModel(
    private val repository: EarthquakeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NusantaraHubUiState())
    val state: StateFlow<NusantaraHubUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun loadLatest(isEnglish: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.load() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            latest = it.latest,
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
            NusantaraHubViewModel(container.earthquakes) as T
    }
}
