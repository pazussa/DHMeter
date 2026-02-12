package com.dhmeter.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.usecase.CreateTrackUseCase
import com.dhmeter.domain.usecase.GetTracksUseCase
import com.dhmeter.sensing.SensorAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SensorStatus(
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasRotationVector: Boolean = false,
    val hasGps: Boolean = false
)

data class HomeUiState(
    val tracks: List<Track> = emptyList(),
    val sensorStatus: SensorStatus = SensorStatus(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
    private val createTrackUseCase: CreateTrackUseCase,
    private val sensorAvailability: SensorAvailability
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTracks()
        checkSensors()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            getTracksUseCase()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { tracks ->
                    _uiState.update { it.copy(tracks = tracks, isLoading = false) }
                }
        }
    }

    private fun checkSensors() {
        val status = SensorStatus(
            hasAccelerometer = sensorAvailability.hasAccelerometer(),
            hasGyroscope = sensorAvailability.hasGyroscope(),
            hasRotationVector = sensorAvailability.hasRotationVector(),
            hasGps = sensorAvailability.hasGps()
        )
        _uiState.update { it.copy(sensorStatus = status) }
    }

    fun createTrack(name: String, locationHint: String?) {
        viewModelScope.launch {
            createTrackUseCase(name, locationHint)
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
