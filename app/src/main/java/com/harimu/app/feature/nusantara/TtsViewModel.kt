package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.TtsRepository
import com.harimu.app.core.nusantara.TtsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TtsUiState(
    val query: String = "",
    val result: TtsResult? = null,
    val isLoading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null
) {
    val answers get() = result?.answers.orEmpty()
}

class TtsViewModel(
    private val repository: TtsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search(isEnglish: Boolean) {
        val clue = _state.value.query.trim()
        if (clue.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, searched = true) }
            val result = withContext(Dispatchers.IO) { repository.answers(clue) }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, result = it, error = null) },
                    onFailure = {
                        current.copy(isLoading = false, error = it.toNusantaraMessage(isEnglish))
                    }
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TtsViewModel(container.tts) as T
    }
}
