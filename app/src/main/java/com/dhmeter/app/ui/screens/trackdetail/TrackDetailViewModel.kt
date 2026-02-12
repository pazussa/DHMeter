package com.dhmeter.app.ui.screens.trackdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.Track
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
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val getRunsByTrackUseCase: GetRunsByTrackUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun loadTrack(trackId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
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
                    _uiState.update { it.copy(runs = runs, isLoading = false) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
