package com.dhmeter.app.ui.screens.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.TrackSegment
import com.dhmeter.domain.usecase.GetTrackByIdUseCase
import com.dhmeter.domain.usecase.GetTrackSegmentsUseCase
import com.dhmeter.domain.usecase.ProcessRunUseCase
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState
import com.dhmeter.sensing.preview.PreviewLocation
import com.dhmeter.sensing.preview.RecordingPreviewManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
    private val getTrackSegmentsUseCase: GetTrackSegmentsUseCase,
    private val recordingManager: RecordingManager,
    private val processRunUseCase: ProcessRunUseCase,
    private val previewManager: RecordingPreviewManager
) : ViewModel() {

    companion object {
        private const val SEGMENT_START_RADIUS_M = 30.0
        private const val SEGMENT_END_RADIUS_M = 30.0
        private const val MAX_PREVIEW_ACCURACY_M = 25f
        private const val AUTO_START_MIN_SPEED_MPS = 2.0f
        private const val AUTO_TRIGGER_COOLDOWN_MS = 20_000L
        private const val MIN_RECORDING_BEFORE_AUTO_STOP_MS = 5_000L
    }

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var startTime: Long = 0
    private var localSegments: List<TrackSegment> = emptyList()
    private var activeAutoSegment: TrackSegment? = null
    private var autoCooldownUntilMs: Long = 0L
    private var isAutoStopInProgress: Boolean = false
    private var isStartingRecording: Boolean = false

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

        viewModelScope.launch {
            getTrackSegmentsUseCase(trackId)
                .onSuccess { segments ->
                    localSegments = segments
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
                        isStartingRecording = false
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true
                            )
                        }
                    }
                    is RecordingState.Recording -> {
                        isStartingRecording = false
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
                        maybeAutoStopAtSegmentEnd(state)
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
                        isStartingRecording = false
                        isAutoStopInProgress = false
                        activeAutoSegment = null
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
        val started = previewManager.startPreview(
            onMetrics = { metrics ->
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
            },
            onLocation = { location ->
                maybeAutoStartAtSegmentStart(location)
            }
        )
        if (!started) return

        _uiState.update { it.copy(isPreviewActive = true) }
    }
    
    private fun stopPreview() {
        previewManager.stopPreview()
        _uiState.update { it.copy(isPreviewActive = false) }
    }

    fun startRecording() {
        startRecordingInternal(autoSegment = null)
    }

    private fun startRecordingInternal(autoSegment: TrackSegment?) {
        if (_uiState.value.isRecording || _uiState.value.isProcessing || isStartingRecording) return

        activeAutoSegment = autoSegment
        isAutoStopInProgress = false
        isStartingRecording = true

        // Stop preview before starting real recording
        stopPreview()

        viewModelScope.launch {
            startTime = System.currentTimeMillis()
            startTimer()

            recordingManager.startRecording(
                trackId = _uiState.value.trackId,
                placement = "POCKET_THIGH"
            ).onFailure { e ->
                isStartingRecording = false
                timerJob?.cancel()
                activeAutoSegment = null
                isAutoStopInProgress = false
                _uiState.update {
                    it.copy(
                        canStartRecording = true,
                        error = e.message
                    )
                }
                startPreview()
            }
        }
    }

    fun stopRecording() {
        isStartingRecording = false
        activeAutoSegment = null
        isAutoStopInProgress = false
        stopRecordingInternal()
    }

    private fun stopRecordingInternal() {
        if (_uiState.value.isProcessing) return
        timerJob?.cancel()

        viewModelScope.launch {
            val captureHandle = recordingManager.stopRecording()

            if (captureHandle == null) {
                isAutoStopInProgress = false
                activeAutoSegment = null
                return@launch
            }

            _uiState.update { it.copy(isProcessing = true) }

            processRunUseCase(captureHandle)
                .onSuccess { runId ->
                    isAutoStopInProgress = false
                    activeAutoSegment = null
                    _uiState.update { it.copy(completedRunId = runId) }
                }
                .onFailure { e ->
                    isAutoStopInProgress = false
                    activeAutoSegment = null
                    _uiState.update { 
                        it.copy(
                            isProcessing = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    private fun maybeAutoStartAtSegmentStart(location: PreviewLocation) {
        val currentState = _uiState.value
        if (currentState.isRecording || currentState.isProcessing) return
        if (localSegments.isEmpty()) return
        if (location.accuracy <= 0f || location.accuracy > MAX_PREVIEW_ACCURACY_M) return
        if (location.speed < AUTO_START_MIN_SPEED_MPS) return

        val now = System.currentTimeMillis()
        if (now < autoCooldownUntilMs) return

        var closestSegment: TrackSegment? = null
        var closestDistance = Double.MAX_VALUE

        for (segment in localSegments) {
            val distance = haversineMeters(
                lat1 = location.latitude,
                lon1 = location.longitude,
                lat2 = segment.start.lat,
                lon2 = segment.start.lon
            )
            if (distance < closestDistance) {
                closestDistance = distance
                closestSegment = segment
            }
        }

        if (closestSegment != null && closestDistance <= SEGMENT_START_RADIUS_M) {
            autoCooldownUntilMs = now + AUTO_TRIGGER_COOLDOWN_MS
            startRecordingInternal(autoSegment = closestSegment)
        }
    }

    private fun maybeAutoStopAtSegmentEnd(state: RecordingState.Recording) {
        if (isAutoStopInProgress) return
        val segment = activeAutoSegment ?: return
        val latitude = state.currentLatitude ?: return
        val longitude = state.currentLongitude ?: return

        val elapsedMs = System.currentTimeMillis() - startTime
        if (elapsedMs < MIN_RECORDING_BEFORE_AUTO_STOP_MS) return

        val distanceToEnd = haversineMeters(
            lat1 = latitude,
            lon1 = longitude,
            lat2 = segment.end.lat,
            lon2 = segment.end.lon
        )

        if (distanceToEnd <= SEGMENT_END_RADIUS_M) {
            isAutoStopInProgress = true
            autoCooldownUntilMs = System.currentTimeMillis() + AUTO_TRIGGER_COOLDOWN_MS
            activeAutoSegment = null
            stopRecordingInternal()
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

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
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
