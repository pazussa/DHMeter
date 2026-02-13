package com.dropindh.app.ui.screens.trackdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.repository.TrackAutoStartRepository
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
    val isAutoStartEnabled: Boolean = false,
    val canEnableAutoStart: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val getRunsByTrackUseCase: GetRunsByTrackUseCase,
    private val trackAutoStartRepository: TrackAutoStartRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackDetailUiState())
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun loadTrack(trackId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val autoStartEnabled = trackAutoStartRepository.isAutoStartEnabled(trackId)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isAutoStartEnabled = autoStartEnabled
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
                    val canEnableAutoStart = runs.isNotEmpty()
                    val currentEnabled = trackAutoStartRepository.isAutoStartEnabled(trackId)
                    val effectiveEnabled = canEnableAutoStart && currentEnabled

                    if (!canEnableAutoStart && currentEnabled) {
                        trackAutoStartRepository.setAutoStartEnabled(trackId, false)
                    }

                    _uiState.update {
                        it.copy(
                            runs = runs,
                            canEnableAutoStart = canEnableAutoStart,
                            isAutoStartEnabled = effectiveEnabled,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        val trackId = _uiState.value.track?.id ?: return
        val canEnableAutoStart = _uiState.value.runs.isNotEmpty()
        val effectiveEnabled = enabled && canEnableAutoStart

        trackAutoStartRepository.setAutoStartEnabled(trackId, effectiveEnabled)
        _uiState.update {
            it.copy(
                canEnableAutoStart = canEnableAutoStart,
                isAutoStartEnabled = effectiveEnabled
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

