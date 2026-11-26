package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.QuranRepository
import com.harimu.app.core.nusantara.QuranSurah
import com.harimu.app.core.nusantara.QuranSurahDetail
import com.harimu.app.core.nusantara.filterSurahs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuranUiState(
    val surahs: List<QuranSurah> = emptyList(),
    val query: String = "",
    val detail: QuranSurahDetail? = null,
    val isLoading: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
) {
    val visible: List<QuranSurah> get() = surahs.filterSurahs(query)
}

class QuranViewModel(
    private val repository: QuranRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuranUiState())
    val state: StateFlow<QuranUiState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun load(isEnglish: Boolean) {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.surahs() }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, surahs = it, loaded = true, error = null) },
                    onFailure = {
                        current.copy(isLoading = false, error = it.toNusantaraMessage(isEnglish))
                    }
                )
            }
        }
    }

    fun openSurah(number: Int, isEnglish: Boolean) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingDetail = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.surah(number) }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoadingDetail = false, detail = it, error = null) },
                    onFailure = {
                        current.copy(isLoadingDetail = false, error = it.toNusantaraMessage(isEnglish))
                    }
                )
            }
        }
    }

    fun closeSurah() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null, isLoadingDetail = false, error = null) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuranViewModel(container.quran) as T
    }
}
