package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.School
import com.harimu.app.core.nusantara.SchoolPage
import com.harimu.app.core.nusantara.SchoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SchoolUiState(
    val query: String = "",
    val schools: List<School> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
)

/** Pencarian sekolah per nama; halaman berikutnya ditambahkan ke daftar yang sama. */
class SchoolViewModel(
    private val repository: SchoolRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolUiState())
    val state: StateFlow<SchoolUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search(isEnglish: Boolean) {
        val query = _state.value.query.trim()
        if (query.length < MIN_QUERY_LENGTH) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(isLoading = true, isLoadingMore = false, error = null, searched = true)
            }
            val result = withContext(Dispatchers.IO) { repository.search(query, page = 1) }
            apply(result, isEnglish) { current, page ->
                current.copy(
                    schools = page.schools,
                    page = page.page,
                    total = page.total,
                    hasMore = page.hasMore,
                    fetchedAtMillis = System.currentTimeMillis()
                )
            }
        }
    }

    fun loadMore(isEnglish: Boolean) {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                repository.search(current.query.trim(), page = current.page + 1)
            }
            apply(result, isEnglish) { state, page ->
                state.copy(
                    schools = (state.schools + page.schools)
                        .distinctBy { school -> school.npsn.ifBlank { school.name } },
                    page = page.page,
                    total = page.total,
                    hasMore = page.hasMore,
                    fetchedAtMillis = System.currentTimeMillis()
                )
            }
        }
    }

    private fun apply(
        result: Result<SchoolPage>,
        isEnglish: Boolean,
        onSuccess: (SchoolUiState, SchoolPage) -> SchoolUiState
    ) {
        _state.update { current ->
            result.fold(
                onSuccess = { page ->
                    onSuccess(current, page).copy(isLoading = false, isLoadingMore = false, error = null)
                },
                onFailure = {
                    current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = it.toNusantaraMessage(isEnglish)
                    )
                }
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SchoolViewModel(container.schools) as T
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}
