package com.dhmeter.app.ui.screens.runsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.usecase.GetComparableRunsUseCase
import com.dhmeter.domain.usecase.GetRunByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunSummaryUiState(
    val run: Run? = null,
    val comparableRuns: List<Run> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RunSummaryViewModel @Inject constructor(
    private val getRunByIdUseCase: GetRunByIdUseCase,
    private val getComparableRunsUseCase: GetComparableRunsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunSummaryUiState())
    val uiState: StateFlow<RunSummaryUiState> = _uiState.asStateFlow()

    fun loadRun(runId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            getRunByIdUseCase(runId)
                .onSuccess { run ->
                    _uiState.update { it.copy(run = run, isLoading = false) }
                    
                    // Load comparable runs
                    if (run.isValid) {
                        loadComparableRuns(run.trackId, runId)
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
}
