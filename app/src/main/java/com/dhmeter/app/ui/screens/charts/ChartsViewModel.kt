package com.dhmeter.app.ui.screens.charts

import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.app.ui.i18n.tr
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.usecase.GetRunEventsUseCase
import com.dhmeter.domain.usecase.GetRunByIdUseCase
import com.dhmeter.domain.usecase.GetRunSeriesUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunChartData(
    val runId: String,
    val runLabel: String,
    val color: Color,
    val run: Run? = null,
    val impactSeries: RunSeries? = null,
    val harshnessSeries: RunSeries? = null,
    val stabilitySeries: RunSeries? = null,
    val speedSeries: RunSeries? = null,
    val events: List<RunEvent> = emptyList()
)

data class ChartsUiState(
    val runs: List<RunChartData> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    // Backwards compatibility helpers
    val impactSeriesA: RunSeries? get() = runs.getOrNull(0)?.impactSeries
    val impactSeriesB: RunSeries? get() = runs.getOrNull(1)?.impactSeries
    val harshnessSeriesA: RunSeries? get() = runs.getOrNull(0)?.harshnessSeries
    val harshnessSeriesB: RunSeries? get() = runs.getOrNull(1)?.harshnessSeries
    val stabilitySeriesA: RunSeries? get() = runs.getOrNull(0)?.stabilitySeries
    val stabilitySeriesB: RunSeries? get() = runs.getOrNull(1)?.stabilitySeries
    val eventsA: List<RunEvent> get() = runs.getOrNull(0)?.events ?: emptyList()
    val eventsB: List<RunEvent> get() = runs.getOrNull(1)?.events ?: emptyList()
}

@HiltViewModel
class ChartsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getRunByIdUseCase: GetRunByIdUseCase,
    private val getRunSeriesUseCase: GetRunSeriesUseCase,
    private val getRunEventsUseCase: GetRunEventsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

    // Color palette for multiple runs
    private val runColors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFFFF5722), // Orange
        Color(0xFF4CAF50), // Green
        Color(0xFF9C27B0), // Purple
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Pink
        Color(0xFF795548)  // Brown
    )

    fun loadChartData(trackId: String, runIds: List<String>) {
        viewModelScope.launch {
            if (trackId.isBlank()) {
                _uiState.update {
                    it.copy(
                        runs = emptyList(),
                        isLoading = false,
                        error = tr(appContext, "Invalid track", "Track invalido")
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, runs = emptyList()) }
            
            try {
                // Load all data for all runs in parallel
                val runsData = runIds.mapIndexed { index, runId ->
                    async {
                        val runDeferred = async { getRunByIdUseCase(runId).getOrNull() }
                        val impact = async { getRunSeriesUseCase(runId, SeriesType.IMPACT_DENSITY) }
                        val harshness = async { getRunSeriesUseCase(runId, SeriesType.HARSHNESS) }
                        val stability = async { getRunSeriesUseCase(runId, SeriesType.STABILITY) }
                        val speed = async { getRunSeriesUseCase(runId, SeriesType.SPEED_TIME) }
                        val events = async { getRunEventsUseCase(runId) }
                        
                        RunChartData(
                            runId = runId,
                            runLabel = tr(
                                appContext,
                                "Run ${index + 1}",
                                "Bajada ${index + 1}"
                            ),
                            color = runColors[index % runColors.size],
                            run = runDeferred.await(),
                            impactSeries = impact.await().getOrNull(),
                            harshnessSeries = harshness.await().getOrNull(),
                            stabilitySeries = stability.await().getOrNull(),
                            speedSeries = speed.await().getOrNull(),
                            events = events.await().getOrDefault(emptyList())
                        )
                    }
                }.awaitAll()
                
                _uiState.update { state ->
                    state.copy(
                        runs = runsData,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false, runs = emptyList()) }
            }
        }
    }

    // Backwards compatibility
    fun loadChartData(trackId: String, runAId: String, runBId: String) {
        loadChartData(trackId, listOf(runAId, runBId))
    }
}
