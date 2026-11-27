package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Doa
import com.harimu.app.core.nusantara.DoaRepository
import com.harimu.app.core.nusantara.filterByQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DoasUiState(
    val doas: List<Doa> = emptyList(),
    val query: String = "",
    val selected: Doa? = null,
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
) {
    val visible: List<Doa> get() = doas.filterByQuery(query)
}

class DoasViewModel(
    private val repository: DoaRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DoasUiState())
    val state: StateFlow<DoasUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun select(doa: Doa?) {
        _state.update { it.copy(selected = if (it.selected?.id == doa?.id) null else doa) }
    }

    fun load(isEnglish: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.all() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = false,
                            doas = it,
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
            DoasViewModel(container.doas) as T
    }
}
