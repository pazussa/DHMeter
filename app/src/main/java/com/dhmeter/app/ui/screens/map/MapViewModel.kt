package com.dhmeter.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.*
import com.dhmeter.domain.usecase.GetRunMapDataUseCase
import com.dhmeter.domain.usecase.GetRunSectionComparisonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val runId: String = "",
    val mapData: RunMapData? = null,
    val sectionComparison: RunMapSectionComparison? = null,
    val showSectionComparison: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedMetric: MapMetricType = MapMetricType.IMPACT,
    val showEvents: Boolean = true,
    val selectedEvent: MapEventMarker? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getRunMapDataUseCase: GetRunMapDataUseCase,
    private val getRunSectionComparisonUseCase: GetRunSectionComparisonUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var metricJob: Job? = null

    fun loadMapData(runId: String) {
        loadJob?.cancel()
        metricJob?.cancel()
        _uiState.update {
            it.copy(
                runId = runId,
                mapData = null,
                sectionComparison = null,
                showSectionComparison = false,
                isLoading = true,
                error = null,
                selectedEvent = null
            )
        }
        
        loadJob = viewModelScope.launch {
            val mapResult = getRunMapDataUseCase(runId)
            val sectionResult = getRunSectionComparisonUseCase(runId)

            mapResult
                .onSuccess { mapData ->
                    _uiState.update {
                        it.copy(
                            mapData = mapData,
                            sectionComparison = sectionResult.getOrNull(),
                            showSectionComparison = false,
                            isLoading = false,
                            error = if (mapData == null) "No GPS data available" else null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            mapData = null,
                            sectionComparison = null,
                            showSectionComparison = false,
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
        val previousMetric = _uiState.value.selectedMetric
        metricJob?.cancel()
        
        _uiState.update { it.copy(selectedMetric = metric, isLoading = true, error = null) }
        
        metricJob = viewModelScope.launch {
            getRunMapDataUseCase.withMetric(currentRunId, metric)
                .onSuccess { mapData ->
                    _uiState.update { 
                        it.copy(
                            mapData = mapData,
                            isLoading = false,
                            selectedEvent = null,
                            error = if (mapData == null) "No GPS data available" else null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            selectedMetric = previousMetric,
                            isLoading = false,
                            error = e.message ?: "Failed to load map data"
                        )
                    }
                }
        }
    }

    fun toggleEvents() {
        _uiState.update { it.copy(showEvents = !it.showEvents) }
    }

    fun selectEvent(event: MapEventMarker?) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    fun dismissEventSheet() {
        _uiState.update { it.copy(selectedEvent = null) }
    }

    fun showSectionComparison() {
        if (_uiState.value.sectionComparison == null) return
        _uiState.update { it.copy(showSectionComparison = true) }
    }

    fun dismissSectionComparison() {
        _uiState.update { it.copy(showSectionComparison = false) }
    }
}
