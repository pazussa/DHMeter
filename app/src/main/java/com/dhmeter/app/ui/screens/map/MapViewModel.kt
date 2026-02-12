package com.dhmeter.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.*
import com.dhmeter.domain.usecase.GetRunMapDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val runId: String = "",
    val mapData: RunMapData? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedMetric: MapMetricType = MapMetricType.IMPACT,
    val mapDisplayType: MapDisplayType = MapDisplayType.TERRAIN,
    val showTraffic: Boolean = false,
    val showEvents: Boolean = true,
    val selectedEvent: MapEventMarker? = null
)

enum class MapDisplayType(val displayName: String) {
    TERRAIN("Terrain"),
    NORMAL("Normal"),
    SATELLITE("Satellite"),
    HYBRID("Hybrid")
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getRunMapDataUseCase: GetRunMapDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun loadMapData(runId: String) {
        _uiState.update { it.copy(runId = runId, isLoading = true) }
        
        viewModelScope.launch {
            getRunMapDataUseCase(runId)
                .onSuccess { mapData ->
                    _uiState.update { 
                        it.copy(
                            mapData = mapData,
                            isLoading = false,
                            error = if (mapData == null) "No GPS data available" else null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load map data"
                        )
                    }
                }
        }
    }

    fun changeMetric(metric: MapMetricType) {
        val currentRunId = _uiState.value.runId
        if (currentRunId.isEmpty()) return
        
        _uiState.update { it.copy(selectedMetric = metric, isLoading = true) }
        
        viewModelScope.launch {
            getRunMapDataUseCase.withMetric(currentRunId, metric)
                .onSuccess { mapData ->
                    _uiState.update { 
                        it.copy(
                            mapData = mapData,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    fun toggleEvents() {
        _uiState.update { it.copy(showEvents = !it.showEvents) }
    }

    fun setMapDisplayType(type: MapDisplayType) {
        _uiState.update { it.copy(mapDisplayType = type) }
    }

    fun toggleTraffic() {
        _uiState.update { it.copy(showTraffic = !it.showTraffic) }
    }

    fun selectEvent(event: MapEventMarker?) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    fun dismissEventSheet() {
        _uiState.update { it.copy(selectedEvent = null) }
    }
}
