package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.SundaneseEntry
import com.harimu.app.core.nusantara.SundaneseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SundaneseUiState(
    val query: String = "",
    val entries: List<SundaneseEntry> = emptyList(),
    val lookedUpWord: String = "",
    val isLoading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
)

class SundaneseViewModel(
    private val repository: SundaneseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SundaneseUiState())
    val state: StateFlow<SundaneseUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun lookupPreset(word: String, isEnglish: Boolean) {
        _state.update { it.copy(query = word) }
        lookup(isEnglish)
    }

    fun lookup(isEnglish: Boolean) {
        val word = _state.value.query.trim()
        if (word.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, searched = true) }
            val result = withContext(Dispatchers.IO) { repository.lookup(word) }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            entries = it,
                            lookedUpWord = word,
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

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SundaneseViewModel(container.sundanese) as T
    }
}
