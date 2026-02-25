package com.dropindh.app.ui.screens.trackdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.usecase.DeleteRunUseCase
import com.dhmeter.domain.usecase.DeleteTrackUseCase
import com.dhmeter.domain.usecase.GetRunsByTrackUseCase
import com.dhmeter.domain.usecase.GetTrackByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackDetailUiState(
    val track: Track? = null,
    val runs: List<Run> = emptyList(),
    val deletingRunId: String? = null,
    val isDeletingTrack: Boolean = false,
    val trackDeleted: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val getRunsByTrackUseCase: GetRunsByTrackUseCase,
    private val deleteRunUseCase: DeleteRunUseCase,
    private val deleteTrackUseCase: DeleteTrackUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun loadTrack(trackId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    trackDeleted = false
                )
            }
            
            // Load track info
            getTrackByIdUseCase(trackId)
                .onSuccess { track ->
                    _uiState.update { it.copy(track = track) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            
            // Load runs
            getRunsByTrackUseCase(trackId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { runs ->
                    _uiState.update {
                        it.copy(
                            runs = runs,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deleteRun(runId: String) {
        if (runId.isBlank() || _uiState.value.deletingRunId != null || _uiState.value.isDeletingTrack) return

        viewModelScope.launch {
            _uiState.update { it.copy(deletingRunId = runId, error = null) }
            deleteRunUseCase(runId)
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            deletingRunId = null,
                            error = error.message
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { state -> state.copy(deletingRunId = null) }
                }
        }
    }

    fun deleteTrack() {
        val trackId = _uiState.value.track?.id ?: return
        if (_uiState.value.isDeletingTrack) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingTrack = true, error = null) }

            deleteTrackUseCase(trackId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isDeletingTrack = false,
                            trackDeleted = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDeletingTrack = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun consumeTrackDeleted() {
        _uiState.update { it.copy(trackDeleted = false) }
    }
}
