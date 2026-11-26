package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Region
import com.harimu.app.core.nusantara.RegionRepository
import com.harimu.app.core.nusantara.VillagePagingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RegionsUiState(
    val provinces: List<Region> = emptyList(),
    val regencies: List<Region> = emptyList(),
    val districts: List<Region> = emptyList(),
    val province: Region? = null,
    val regency: Region? = null,
    val district: Region? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class RegionsViewModel(
    private val repository: RegionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegionsUiState())
    val state: StateFlow<RegionsUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var villagePager: Flow<PagingData<Region>>? = null
    private var pagerDistrictId: String? = null

    fun loadProvinces(isEnglish: Boolean) {
        if (_state.value.provinces.isNotEmpty()) return
        run(isEnglish) {
            val list = repository.provinces().getOrElse { throw it }
            _state.update { current -> current.copy(provinces = list) }
        }
    }

    fun selectProvince(region: Region, isEnglish: Boolean) {
        _state.update {
            it.copy(
                province = region,
                regency = null,
                district = null,
                regencies = emptyList(),
                districts = emptyList(),
                error = null
            )
        }
        resetPager()
        run(isEnglish) {
            val list = repository.regencies(region.id).getOrElse { throw it }
            _state.update { current -> current.copy(regencies = list) }
        }
    }

    fun selectRegency(region: Region, isEnglish: Boolean) {
        _state.update { it.copy(regency = region, district = null, districts = emptyList(), error = null) }
        resetPager()
        run(isEnglish) {
            val list = repository.districts(region.id).getOrElse { throw it }
            _state.update { current -> current.copy(districts = list) }
        }
    }

    fun selectDistrict(region: Region) {
        _state.update { it.copy(district = region, error = null) }
    }

    /** Daftar desa dimuat bertahap dengan Paging; pager di-cache per kecamatan. */
    fun villages(districtId: String): Flow<PagingData<Region>> {
        if (pagerDistrictId != districtId) {
            pagerDistrictId = districtId
            villagePager = Pager(
                config = PagingConfig(pageSize = VillagePagingSource.PAGE_SIZE, enablePlaceholders = false)
            ) { repository.villagePagingSource(districtId) }.flow.cachedIn(viewModelScope)
        }
        return requireNotNull(villagePager)
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

    private fun resetPager() {
        pagerDistrictId = null
        villagePager = null
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
            RegionsViewModel(container.regions) as T
    }
}
