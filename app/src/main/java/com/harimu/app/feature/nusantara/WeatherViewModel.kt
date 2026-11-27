package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Region
import com.harimu.app.core.nusantara.RegionRepository
import com.harimu.app.core.nusantara.WeatherForecast
import com.harimu.app.core.nusantara.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WeatherUiState(
    val provinces: List<Region> = emptyList(),
    val regencies: List<Region> = emptyList(),
    val districts: List<Region> = emptyList(),
    val villages: List<Region> = emptyList(),
    val province: Region? = null,
    val regency: Region? = null,
    val district: Region? = null,
    val village: Region? = null,
    val regionFilter: String = "",
    val directAdm4: String? = null,
    val isLoading: Boolean = false,
    val forecast: WeatherForecast? = null,
    val error: String? = null,
    val fetchedAtMillis: Long? = null
) {
    val visibleProvinces: List<Region> get() = provinces.filterRegionName(regionFilter)
    val visibleRegencies: List<Region> get() = regencies.filterRegionName(regionFilter)
    val visibleDistricts: List<Region> get() = districts.filterRegionName(regionFilter)
    val visibleVillages: List<Region> get() = villages.filterRegionName(regionFilter)
}

internal fun List<Region>.filterRegionName(query: String): List<Region> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { it.name.lowercase().contains(needle) || it.id.contains(needle) }
}

class WeatherViewModel(
    private val regions: RegionRepository,
    private val weather: WeatherRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var provincesJob: Job? = null

    fun onRegionFilterChange(value: String) {
        _state.update { it.copy(regionFilter = value) }
    }

    fun loadProvinces(isEnglish: Boolean) {
        provincesJob?.cancel()
        provincesJob = viewModelScope.launch {
            if (_state.value.provinces.isEmpty() && _state.value.forecast == null) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            val result = withContext(Dispatchers.IO) { regions.provinces() }
            _state.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isLoading = job?.isActive == true,
                            provinces = it,
                            error = null,
                            fetchedAtMillis = current.fetchedAtMillis ?: System.currentTimeMillis()
                        )
                    },
                    onFailure = {
                        current.copy(
                            isLoading = job?.isActive == true,
                            error = current.error ?: it.toNusantaraMessage(isEnglish)
                        )
                    }
                )
            }
        }
    }

    fun loadForecastForAdm4(adm4: String, isEnglish: Boolean) {
        val clean = adm4.trim()
        if (clean.isEmpty()) return
        _state.update { it.copy(directAdm4 = clean, error = null) }
        loadForecast(clean, isEnglish)
    }

    fun selectProvince(region: Region, isEnglish: Boolean) {
        _state.update {
            it.copy(
                province = region,
                regency = null,
                district = null,
                village = null,
                regencies = emptyList(),
                districts = emptyList(),
                villages = emptyList(),
                regionFilter = "",
                directAdm4 = null,
                forecast = null,
                error = null
            )
        }
        loadRegencies(region.id, isEnglish)
    }

    fun selectRegency(region: Region, isEnglish: Boolean) {
        _state.update {
            it.copy(
                regency = region,
                district = null,
                village = null,
                districts = emptyList(),
                villages = emptyList(),
                regionFilter = "",
                directAdm4 = null,
                forecast = null,
                error = null
            )
        }
        loadDistricts(region.id, isEnglish)
    }

    fun selectDistrict(region: Region, isEnglish: Boolean) {
        _state.update {
            it.copy(
                district = region,
                village = null,
                villages = emptyList(),
                regionFilter = "",
                directAdm4 = null,
                forecast = null,
                error = null
            )
        }
        loadVillages(region.id, isEnglish)
    }

    fun selectVillage(region: Region, isEnglish: Boolean) {
        _state.update { it.copy(village = region, regionFilter = "", directAdm4 = null, error = null) }
        loadForecast(region.id, isEnglish)
    }

    fun retry(isEnglish: Boolean) {
        val current = _state.value
        when {
            current.village != null -> loadForecast(current.village.id, isEnglish)
            !current.directAdm4.isNullOrBlank() -> loadForecast(current.directAdm4, isEnglish)
            current.district != null -> loadVillages(current.district.id, isEnglish)
            current.regency != null -> loadDistricts(current.regency.id, isEnglish)
            current.province != null -> loadRegencies(current.province.id, isEnglish)
            else -> loadProvinces(isEnglish)
        }
    }

    private fun loadRegencies(provinceId: String, isEnglish: Boolean) = runStep(isEnglish) {
        val list = regions.regencies(provinceId).getOrElse { throw it }
        _state.update { current ->
            current.copy(regencies = list, fetchedAtMillis = System.currentTimeMillis())
        }
    }

    private fun loadDistricts(regencyId: String, isEnglish: Boolean) = runStep(isEnglish) {
        val list = regions.districts(regencyId).getOrElse { throw it }
        _state.update { current ->
            current.copy(districts = list, fetchedAtMillis = System.currentTimeMillis())
        }
    }

    private fun loadVillages(districtId: String, isEnglish: Boolean) = runStep(isEnglish) {
        val list = regions.villages(districtId).getOrElse { throw it }
        _state.update { current ->
            current.copy(villages = list, fetchedAtMillis = System.currentTimeMillis())
        }
    }

    private fun loadForecast(adm4: String, isEnglish: Boolean) = runStep(isEnglish) {
        val forecast = weather.load(adm4).getOrElse { throw it }
        _state.update { current ->
            current.copy(forecast = forecast, fetchedAtMillis = System.currentTimeMillis())
        }
    }

    private fun runStep(isEnglish: Boolean, step: suspend () -> Unit) {
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
            WeatherViewModel(container.regions, container.weather) as T
    }
}
