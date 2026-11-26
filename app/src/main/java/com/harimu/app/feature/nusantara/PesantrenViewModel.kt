package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Pesantren
import com.harimu.app.core.nusantara.PesantrenRegion
import com.harimu.app.core.nusantara.PesantrenRepository
import com.harimu.app.core.nusantara.filterPesantren
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PesantrenUiState(
    val provinces: List<PesantrenRegion> = emptyList(),
    val regencies: List<PesantrenRegion> = emptyList(),
    val pesantren: List<Pesantren> = emptyList(),
    val province: PesantrenRegion? = null,
    val regency: PesantrenRegion? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val visiblePesantren: List<Pesantren> get() = pesantren.filterPesantren(query)
}

class PesantrenViewModel(
    private val repository: PesantrenRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PesantrenUiState())
    val state: StateFlow<PesantrenUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun loadProvinces(isEnglish: Boolean) {
        if (_state.value.provinces.isNotEmpty()) return
        run(isEnglish) {
            val list = repository.provinces().getOrElse { throw it }
            _state.update { current -> current.copy(provinces = list) }
        }
    }

    fun selectProvince(region: PesantrenRegion, isEnglish: Boolean) {
        _state.update {
            it.copy(province = region, regency = null, regencies = emptyList(), pesantren = emptyList())
        }
        run(isEnglish) {
            val list = repository.regencies(region.id).getOrElse { throw it }
            _state.update { current -> current.copy(regencies = list) }
        }
    }

    fun selectRegency(region: PesantrenRegion, isEnglish: Boolean) {
        _state.update { it.copy(regency = region, pesantren = emptyList(), query = "") }
        run(isEnglish) {
            val list = repository.pesantren(region.id).getOrElse { throw it }
            _state.update { current -> current.copy(pesantren = list) }
        }
    }

    fun backToProvinces() {
        job?.cancel()
        _state.update {
            it.copy(province = null, regency = null, regencies = emptyList(), pesantren = emptyList(), error = null)
        }
    }

    fun backToRegencies() {
        job?.cancel()
        _state.update { it.copy(regency = null, pesantren = emptyList(), query = "", error = null) }
    }

    fun retry(isEnglish: Boolean) {
        val current = _state.value
        when {
            current.regency != null -> selectRegency(current.regency, isEnglish)
            current.province != null -> selectProvince(current.province, isEnglish)
            else -> {
                _state.update { it.copy(provinces = emptyList()) }
                loadProvinces(isEnglish)
            }
        }
    }

    private fun run(isEnglish: Boolean, step: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val failure = withContext(Dispatchers.IO) {
                try {
                    step()
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    t
                }
            }
            _state.update { it.copy(isLoading = false, error = failure?.toNusantaraMessage(isEnglish)) }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PesantrenViewModel(container.pesantren) as T
    }
}
