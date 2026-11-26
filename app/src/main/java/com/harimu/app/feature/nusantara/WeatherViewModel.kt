package com.harimu.app.feature.nusantara

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Region
import com.harimu.app.core.nusantara.RegionRepository
import com.harimu.app.core.nusantara.WeatherForecast
import com.harimu.app.core.nusantara.WeatherRepository
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
    val isLoading: Boolean = false,
    val forecast: WeatherForecast? = null,
    val error: String? = null
)

class WeatherViewModel(
    private val regions: RegionRepository,
    private val weather: WeatherRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun loadProvinces(isEnglish: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { regions.provinces() }
            _state.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, provinces = it, error = null) },
                    onFailure = { current.copy(isLoading = false, error = it.toNusantaraMessage(isEnglish)) }
                )
            }
        }
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
                forecast = null,
                error = null
            )
        }
        loadDistricts(region.id, isEnglish)
    }

    fun selectDistrict(region: Region, isEnglish: Boolean) {
        _state.update {
            it.copy(district = region, village = null, villages = emptyList(), forecast = null, error = null)
        }
        loadVillages(region.id, isEnglish)
    }

    fun selectVillage(region: Region, isEnglish: Boolean) {
        _state.update { it.copy(village = region, error = null) }
        loadForecast(region.id, isEnglish)
    }

    fun retry(isEnglish: Boolean) {
        val current = _state.value
        when {
            current.village != null -> loadForecast(current.village.id, isEnglish)
            current.district != null -> loadVillages(current.district.id, isEnglish)
            current.regency != null -> loadDistricts(current.regency.id, isEnglish)
            current.province != null -> loadRegencies(current.province.id, isEnglish)
            else -> loadProvinces(isEnglish)
        }
    }

    private fun loadRegencies(provinceId: String, isEnglish: Boolean) = runStep(isEnglish) {
        val list = regions.regencies(provinceId).getOrElse { throw it }
        _state.update { current -> current.copy(regencies = list) }
    }

    private fun loadDistricts(regencyId: String, isEnglish: Boolean) = runStep(isEnglish) {
        val list = regions.districts(regencyId).getOrElse { throw it }
        _state.update { current -> current.copy(districts = list) }
    }

    private fun loadVillages(districtId: String, isEnglish: Boolean) = runStep(isEnglish) {
        val list = regions.villages(districtId).getOrElse { throw it }
        _state.update { current -> current.copy(villages = list) }
    }

    private fun loadForecast(adm4: String, isEnglish: Boolean) = runStep(isEnglish) {
        val forecast = weather.load(adm4).getOrElse { throw it }
        _state.update { current -> current.copy(forecast = forecast) }
    }

    private fun runStep(isEnglish: Boolean, step: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val failure = withContext(Dispatchers.IO) {
                try {
                    step()
                    null
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
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
