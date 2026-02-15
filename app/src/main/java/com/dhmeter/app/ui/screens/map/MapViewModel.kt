package com.dropindh.app.ui.screens.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropindh.app.ui.i18n.tr
import com.dhmeter.domain.model.*
import com.dhmeter.domain.usecase.GetRunMapDataUseCase
import com.dhmeter.domain.usecase.GetRunSectionComparisonUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MapUiState(
    val runId: String = "",
    val mapData: RunMapData? = null,
    val sectionComparison: RunMapSectionComparison? = null,
    val showSectionComparison: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedMetric: MapMetricType = MapMetricType.SPEED,
    val selectedEvent: MapEventMarker? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
                selectedMetric = MapMetricType.SPEED,
                selectedEvent = null
            )
        }
        
        loadJob = viewModelScope.launch {
            val (mapResult, sectionResult) = withContext(Dispatchers.Default) {
                Pair(
                    getRunMapDataUseCase(runId),
                    getRunSectionComparisonUseCase(runId)
                )
            }

            mapResult
                .onSuccess { mapData ->
                    _uiState.update {
                        it.copy(
                            mapData = mapData,
                            sectionComparison = sectionResult.getOrNull(),
                            showSectionComparison = false,
                            isLoading = false,
                            error = if (mapData == null) {
                                tr(appContext, "No GPS data available", "No hay datos GPS disponibles")
                            } else {
                                null
                            }
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
                            error = e.message ?: tr(
                                appContext,
                                "Failed to load map data",
                                "No se pudieron cargar los datos del mapa"
                            )
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
            val mapResult = withContext(Dispatchers.Default) {
                getRunMapDataUseCase.withMetric(currentRunId, metric)
            }
            mapResult
                .onSuccess { mapData ->
                    _uiState.update { 
                        it.copy(
                            mapData = mapData,
                            isLoading = false,
                            selectedEvent = null,
                            error = if (mapData == null) {
                                tr(appContext, "No GPS data available", "No hay datos GPS disponibles")
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            selectedMetric = previousMetric,
                            isLoading = false,
                            error = e.message ?: tr(
                                appContext,
                                "Failed to load map data",
                                "No se pudieron cargar los datos del mapa"
                            )
                        )
                    }
                }
        }
    }

    fun selectEvent(event: MapEventMarker?) {
        _uiState.update {
            it.copy(
                selectedEvent = event,
                showSectionComparison = if (event != null) false else it.showSectionComparison
            )
        }
    }

    fun dismissEventSheet() {
        _uiState.update { it.copy(selectedEvent = null) }
    }

    fun showSectionComparison() {
        if (_uiState.value.sectionComparison == null) return
        _uiState.update {
            it.copy(
                showSectionComparison = true,
                selectedEvent = null
            )
        }
    }

    fun dismissSectionComparison() {
        _uiState.update { it.copy(showSectionComparison = false) }
    }
}

