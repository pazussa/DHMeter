package com.dhmeter.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.usecase.GetRunCountByTrackUseCase
import com.dhmeter.domain.usecase.GetTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val tracks: List<Track> = emptyList(),
    val runCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
    private val getRunCountByTrackUseCase: GetRunCountByTrackUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            getTracksUseCase()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { tracks ->
                    _uiState.update { it.copy(tracks = tracks, isLoading = false) }
                    loadRunCounts(tracks)
                }
        }
    }

    private suspend fun loadRunCounts(tracks: List<Track>) {
        val counts = mutableMapOf<String, Int>()
        tracks.forEach { track ->
            getRunCountByTrackUseCase(track.id)
                .onSuccess { count ->
                    counts[track.id] = count
                }
        }
        _uiState.update { it.copy(runCounts = counts) }
    }
}
