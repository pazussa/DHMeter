package com.dhmeter.app.ui.screens.runsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.usecase.GetComparableRunsUseCase
import com.dhmeter.domain.usecase.GetRunByIdUseCase
import com.dhmeter.domain.usecase.GetRunEventsUseCase
import com.dhmeter.domain.usecase.GetRunSeriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunSummaryUiState(
    val run: Run? = null,
    val comparableRuns: List<Run> = emptyList(),
    val impactSeries: RunSeries? = null,
    val harshnessSeries: RunSeries? = null,
    val stabilitySeries: RunSeries? = null,
    val events: List<RunEvent> = emptyList(),
    val isChartsLoading: Boolean = false,
    val chartsError: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RunSummaryViewModel @Inject constructor(
    private val getRunByIdUseCase: GetRunByIdUseCase,
    private val getComparableRunsUseCase: GetComparableRunsUseCase,
    private val getRunSeriesUseCase: GetRunSeriesUseCase,
    private val getRunEventsUseCase: GetRunEventsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunSummaryUiState())
    val uiState: StateFlow<RunSummaryUiState> = _uiState.asStateFlow()

    fun loadRun(runId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    comparableRuns = emptyList(),
                    impactSeries = null,
                    harshnessSeries = null,
                    stabilitySeries = null,
                    events = emptyList(),
                    isChartsLoading = false,
                    chartsError = null
                )
            }
            
            getRunByIdUseCase(runId)
                .onSuccess { run ->
                    _uiState.update { it.copy(run = run, isLoading = false, isChartsLoading = true) }
                    
                    // Load comparable runs
                    if (run.isValid) {
                        viewModelScope.launch {
                            loadComparableRuns(run.trackId, runId)
                        }
                    }

                    viewModelScope.launch {
                        loadRunCharts(runId)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    private suspend fun loadComparableRuns(trackId: String, currentRunId: String) {
        getComparableRunsUseCase(trackId, currentRunId)
            .onSuccess { runs ->
                _uiState.update { it.copy(comparableRuns = runs) }
            }
    }

    private suspend fun loadRunCharts(runId: String) {
        _uiState.update { it.copy(isChartsLoading = true, chartsError = null) }

        try {
            coroutineScope {
                val impactDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.IMPACT_DENSITY).getOrNull()
                }
                val harshnessDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.HARSHNESS).getOrNull()
                }
                val stabilityDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.STABILITY).getOrNull()
                }
                val eventsDeferred = async {
                    getRunEventsUseCase(runId).getOrDefault(emptyList())
                }

                _uiState.update {
                    it.copy(
                        impactSeries = impactDeferred.await(),
                        harshnessSeries = harshnessDeferred.await(),
                        stabilitySeries = stabilityDeferred.await(),
                        events = eventsDeferred.await(),
                        isChartsLoading = false,
                        chartsError = null
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isChartsLoading = false,
                    chartsError = e.message ?: "Failed to load charts"
                )
            }
        }
    }
}
