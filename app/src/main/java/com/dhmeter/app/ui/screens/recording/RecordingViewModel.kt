package com.dhmeter.app.ui.screens.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.usecase.GetTrackByIdUseCase
import com.dhmeter.domain.usecase.ProcessRunUseCase
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState
import com.dhmeter.sensing.collector.ImuCollector
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.sensing.monitor.LiveMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingUiState(
    val trackId: String = "",
    val trackName: String = "",
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val elapsedSeconds: Long = 0,
    val gpsSignal: GpsSignalLevel = GpsSignalLevel.NONE,
    val movementDetected: Boolean = false,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    val currentSpeed: Float = 0f,
    val canStartRecording: Boolean = true,
    val completedRunId: String? = null,
    val error: String? = null,
    // Live metrics (0-1 normalized)
    val liveImpact: Float = 0f,
    val liveHarshness: Float = 0f,
    val liveStability: Float = 0f,
    // Preview mode
    val isPreviewActive: Boolean = false
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val recordingManager: RecordingManager,
    private val processRunUseCase: ProcessRunUseCase,
    private val imuCollector: ImuCollector,
    private val liveMonitor: LiveMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var previewJob: Job? = null
    private var previewBuffers: SensorBuffers? = null
    private var startTime: Long = 0

    fun initialize(trackId: String) {
        _uiState.update { it.copy(trackId = trackId) }
        
        viewModelScope.launch {
            getTrackByIdUseCase(trackId)
                .onSuccess { track ->
                    _uiState.update { it.copy(trackName = track.name) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }

        // Start live preview immediately
        startPreview()

        // Observe recording state
        viewModelScope.launch {
            recordingManager.recordingState.collect { state ->
                when (state) {
                    is RecordingState.Idle -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true
                            )
                        }
                    }
                    is RecordingState.Recording -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = true,
                                isProcessing = false,
                                canStartRecording = false,
                                gpsSignal = mapGpsSignal(state.gpsAccuracy),
                                movementDetected = state.movementDetected,
                                signalQuality = mapSignalQuality(state.signalStability),
                                currentSpeed = state.currentSpeed,
                                liveImpact = state.liveImpact,
                                liveHarshness = state.liveHarshness,
                                liveStability = state.liveStability,
                                isPreviewActive = false
                            )
                        }
                    }
                    is RecordingState.Processing -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = true,
                                canStartRecording = false
                            )
                        }
                    }
                    is RecordingState.Completed -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                completedRunId = state.runId
                            )
                        }
                    }
                    is RecordingState.Error -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true,
                                error = state.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startPreview() {
        if (previewJob?.isActive == true) return
        
        previewBuffers = SensorBuffers()
        
        val started = imuCollector.start(previewBuffers!!)
        if (!started) return
        
        _uiState.update { it.copy(isPreviewActive = true) }
        
        previewJob = viewModelScope.launch {
            liveMonitor.startMonitoring(previewBuffers!!) { metrics ->
                // Only update if not recording (recording uses its own metrics)
                if (!_uiState.value.isRecording) {
                    _uiState.update { state ->
                        state.copy(
                            liveImpact = metrics.liveImpact,
                            liveHarshness = metrics.liveHarshness,
                            liveStability = metrics.liveStability
                        )
                    }
                }
            }
        }
    }
    
    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        liveMonitor.stopMonitoring()
        imuCollector.stop()
        previewBuffers?.clear()
        previewBuffers = null
        _uiState.update { it.copy(isPreviewActive = false) }
    }

    fun startRecording() {
        // Stop preview before starting real recording
        stopPreview()
        
        viewModelScope.launch {
            startTime = System.currentTimeMillis()
            startTimer()
            
            recordingManager.startRecording(
                trackId = _uiState.value.trackId,
                placement = "POCKET_THIGH"
            )
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        
        viewModelScope.launch {
            val captureHandle = recordingManager.stopRecording()
            
            captureHandle?.let { handle ->
                _uiState.update { it.copy(isProcessing = true) }
                
                processRunUseCase(handle)
                    .onSuccess { runId ->
                        _uiState.update { it.copy(completedRunId = runId) }
                    }
                    .onFailure { e ->
                        _uiState.update { 
                            it.copy(
                                isProcessing = false,
                                error = e.message
                            )
                        }
                    }
            }
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                _uiState.update { it.copy(elapsedSeconds = elapsed) }
            }
        }
    }

    private fun mapGpsSignal(accuracy: Float): GpsSignalLevel {
        return when {
            accuracy < 0 -> GpsSignalLevel.NONE
            accuracy <= 5 -> GpsSignalLevel.GOOD
            accuracy <= 15 -> GpsSignalLevel.MEDIUM
            else -> GpsSignalLevel.POOR
        }
    }

    private fun mapSignalQuality(stability: Float): SignalQuality {
        return when {
            stability < 0 -> SignalQuality.UNKNOWN
            stability >= 0.8f -> SignalQuality.STABLE
            stability >= 0.5f -> SignalQuality.MODERATE
            else -> SignalQuality.LOOSE
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        stopPreview()
    }
}
