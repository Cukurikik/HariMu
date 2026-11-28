package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.EarthquakeRepository
import com.harimu.app.core.nusantara.EarthquakeSnapshot
import com.harimu.app.core.nusantara.NusantaraException
import com.harimu.app.core.nusantara.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EarthquakeUiState(
    val isLoading: Boolean = false,
    val snapshot: EarthquakeSnapshot? = null,
    val error: String? = null,
    /** True ketika snapshot yang tampil berasal dari pemuatan sebelumnya dan pembaruan gagal. */
    val isStale: Boolean = false,
    val fetchedAtMillis: Long? = null
)

class EarthquakeViewModel(
    private val repository: EarthquakeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EarthquakeUiState())
    val state: StateFlow<EarthquakeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(isEnglish: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.load() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            snapshot = it,
                            error = null,
                            fetchedAtMillis = System.currentTimeMillis()
                        )
                    },
                    onFailure = { failure ->
                        current.copy(
                            isLoading = false,
                            error = failure.toNusantaraMessage(isEnglish),
                            isStale = current.snapshot != null
                        )
                    }
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EarthquakeViewModel(container.earthquakes) as T
    }
}

internal fun Throwable.toNusantaraMessage(isEnglish: Boolean): String =
    (this as? NusantaraException)?.userMessage(isEnglish)
        ?: localizedMessage
        ?: if (isEnglish) "Could not load Indonesian public data." else "Gagal memuat data publik Indonesia."
