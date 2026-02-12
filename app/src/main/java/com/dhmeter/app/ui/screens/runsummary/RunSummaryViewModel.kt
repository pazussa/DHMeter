package com.dhmeter.app.ui.screens.runsummary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.app.ui.i18n.tr
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.usecase.GetComparableRunsUseCase
import com.dhmeter.domain.usecase.GetRunByIdUseCase
import com.dhmeter.domain.usecase.GetRunEventsUseCase
import com.dhmeter.domain.usecase.GetRunSeriesUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunSummaryUiState(
    val run: Run? = null,
    val comparableRuns: List<Run> = emptyList(),
    val impactSeries: RunSeries? = null,
    val harshnessSeries: RunSeries? = null,
    val stabilitySeries: RunSeries? = null,
    val speedSeries: RunSeries? = null,
    val events: List<RunEvent> = emptyList(),
    val isChartsLoading: Boolean = false,
    val chartsError: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RunSummaryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getRunByIdUseCase: GetRunByIdUseCase,
    private val getComparableRunsUseCase: GetComparableRunsUseCase,
    private val getRunSeriesUseCase: GetRunSeriesUseCase,
    private val getRunEventsUseCase: GetRunEventsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunSummaryUiState())
    val uiState: StateFlow<RunSummaryUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var compareJob: Job? = null
    private var chartsJob: Job? = null

    fun loadRun(runId: String) {
        loadJob?.cancel()
        compareJob?.cancel()
        chartsJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    run = null,
                    isLoading = true,
                    error = null,
                    comparableRuns = emptyList(),
                    impactSeries = null,
                    harshnessSeries = null,
                    stabilitySeries = null,
                    speedSeries = null,
                    events = emptyList(),
                    isChartsLoading = false,
                    chartsError = null
                )
            }
            
            getRunByIdUseCase(runId)
                .onSuccess { run ->
                    _uiState.update { it.copy(run = run, isLoading = false, isChartsLoading = true) }
                    
                    // Load comparable runs
                    compareJob = viewModelScope.launch {
                        loadComparableRuns(run.trackId, runId)
                    }

                    chartsJob = viewModelScope.launch {
                        loadRunCharts(runId)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(run = null, error = e.message, isLoading = false) }
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
                val speedDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.SPEED_TIME).getOrNull()
                }
                val eventsDeferred = async {
                    getRunEventsUseCase(runId).getOrDefault(emptyList())
                }

                _uiState.update {
                    it.copy(
                        impactSeries = impactDeferred.await(),
                        harshnessSeries = harshnessDeferred.await(),
                        stabilitySeries = stabilityDeferred.await(),
                        speedSeries = speedDeferred.await(),
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
                    chartsError = e.message ?: tr(
                        appContext,
                        "Failed to load charts",
                        "No se pudieron cargar las graficas"
                    )
                )
            }
        }
    }
}
