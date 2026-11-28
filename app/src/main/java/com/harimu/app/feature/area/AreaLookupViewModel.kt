package com.harimu.app.feature.area

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.area.AreaEntry
import com.harimu.app.core.area.AreaLookupException
import com.harimu.app.core.area.AreaLookupRepository
import com.harimu.app.core.area.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AreaLookupUiState(
    val keyword: String = "",
    val isLoading: Boolean = false,
    val entries: List<AreaEntry> = emptyList(),
    val hasSearched: Boolean = false,
    val error: String? = null,
    /** Kata kunci yang benar-benar menghasilkan [entries] saat ini. */
    val resultKeyword: String = "",
    /** True ketika [entries] berasal dari pencarian sebelumnya dan pencarian ulang gagal. */
    val isStale: Boolean = false,
    val fetchedAtMillis: Long? = null
)

class AreaLookupViewModel(
    private val repository: AreaLookupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AreaLookupUiState())
    val state: StateFlow<AreaLookupUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value, error = null) }
    }

    fun selectPresetAndSearch(keyword: String, isEnglish: Boolean) {
        _state.update { it.copy(keyword = keyword, error = null) }
        search(isEnglish)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                keyword = "",
                isLoading = false,
                entries = emptyList(),
                hasSearched = false,
                error = null,
                resultKeyword = "",
                isStale = false
            )
        }
    }

    fun search(isEnglish: Boolean) {
        val query = _state.value.keyword
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.load(query) }
            _state.update { current ->
                result.fold(
                    onSuccess = { entries ->
                        current.copy(
                            isLoading = false,
                            entries = entries,
                            hasSearched = true,
                            error = null,
                            resultKeyword = current.keyword.trim(),
                            isStale = false,
                            fetchedAtMillis = System.currentTimeMillis()
                        )
                    },
                    onFailure = { failure ->
                        val message = (failure as? AreaLookupException)?.userMessage(isEnglish)
                            ?: failure.localizedMessage
                            ?: if (isEnglish) "Could not search the area." else "Gagal mencari wilayah."
                        current.copy(
                            isLoading = false,
                            error = message,
                            isStale = current.entries.isNotEmpty()
                        )
                    }
                )
            }
        }
    }

    fun refresh(isEnglish: Boolean) = search(isEnglish)

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AreaLookupViewModel(container.areaLookup) as T
    }
}
