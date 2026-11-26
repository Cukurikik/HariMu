package com.harimu.app.feature.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.prayer.PrayerErrorKind
import com.harimu.app.core.prayer.PrayerSchedule
import com.harimu.app.core.prayer.PrayerTimesException
import com.harimu.app.core.prayer.PrayerTimesRepository
import com.harimu.app.core.prayer.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PrayerTimesUiState(
    val cityQuery: String = "Jakarta",
    val isLoading: Boolean = false,
    val schedule: PrayerSchedule? = null,
    val error: String? = null
)

class PrayerTimesViewModel(
    private val repository: PrayerTimesRepository,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _state = MutableStateFlow(PrayerTimesUiState())
    val state: StateFlow<PrayerTimesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onCityQueryChange(value: String) {
        _state.update { it.copy(cityQuery = value, error = null) }
    }

    fun search(isEnglish: Boolean) {
        val query = _state.value.cityQuery
        if (query.trim().isEmpty()) {
            _state.update {
                it.copy(error = PrayerTimesException(PrayerErrorKind.EMPTY_QUERY).userMessage(isEnglish))
            }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.load(query, today()) }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, schedule = it, error = null) },
                    onFailure = { failure ->
                        val message = (failure as? PrayerTimesException)?.userMessage(isEnglish)
                            ?: failure.localizedMessage
                            ?: if (isEnglish) "Could not load the prayer schedule." else "Gagal memuat jadwal sholat."
                        current.copy(isLoading = false, error = message)
                    }
                )
            }
        }
    }

    fun refresh(isEnglish: Boolean) = search(isEnglish)

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PrayerTimesViewModel(container.prayerTimes) as T
    }
}
