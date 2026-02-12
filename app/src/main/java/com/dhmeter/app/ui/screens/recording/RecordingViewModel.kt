package com.dhmeter.app.ui.screens.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.app.service.RecordingService
import com.dhmeter.domain.model.TrackSegment
import com.dhmeter.domain.usecase.GetTrackByIdUseCase
import com.dhmeter.domain.usecase.GetTrackSegmentsUseCase
import com.dhmeter.domain.usecase.ProcessRunUseCase
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState
import com.dhmeter.sensing.preview.PreviewLocation
import com.dhmeter.sensing.preview.RecordingPreviewManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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
    val segmentCount: Int = 0,
    val segmentStatus: String = "Loading local segments...",
    // Live metrics (0-1 normalized)
    val liveImpact: Float = 0f,
    val liveHarshness: Float = 0f,
    val liveStability: Float = 0f,
    // Preview mode
    val isPreviewActive: Boolean = false
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val getTrackSegmentsUseCase: GetTrackSegmentsUseCase,
    private val recordingManager: RecordingManager,
    private val processRunUseCase: ProcessRunUseCase,
    private val previewManager: RecordingPreviewManager
) : ViewModel() {

    companion object {
        private const val SEGMENT_START_ARM_RADIUS_M = 40.0
        private const val SEGMENT_START_TRIGGER_RADIUS_M = 24.0
        private const val SEGMENT_START_RELEASE_RADIUS_M = 55.0
        private const val SEGMENT_END_RADIUS_M = 24.0
        private const val MAX_PREVIEW_ACCURACY_M = 25f
        private const val AUTO_START_MIN_SPEED_MPS = 2.5f
        private const val AUTO_TRIGGER_COOLDOWN_MS = 20_000L
        private const val MIN_RECORDING_BEFORE_AUTO_STOP_MS = 5_000L
        private const val MIN_START_CONFIRMATION_HITS = 2
        private const val MIN_DIRECTION_DOT = 0.15
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
    private var recordingStateJob: Job? = null
    private var startCandidateSegment: TrackSegment? = null
    private var startCandidateHits: Int = 0
    private var lastPreviewLatitude: Double? = null
    private var lastPreviewLongitude: Double? = null
    private var lastRecordingLatitude: Double? = null
    private var lastRecordingLongitude: Double? = null
    private var lastDistanceToSegmentEndM: Double? = null

    fun initialize(trackId: String) {
        localSegments = emptyList()
        activeAutoSegment = null
        isAutoStopInProgress = false
        isStartingRecording = false
        startCandidateSegment = null
        startCandidateHits = 0
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null

        _uiState.update {
            it.copy(
                trackId = trackId,
                isRecording = false,
                isProcessing = false,
                elapsedSeconds = 0,
                canStartRecording = true,
                completedRunId = null,
                error = null,
                segmentCount = 0,
                segmentStatus = "Loading local segments..."
            )
        }

        if (recordingManager.recordingState.value is RecordingState.Processing) {
            recordingManager.cancelRecording()
        }
        
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
                    _uiState.update {
                        it.copy(
                            segmentCount = segments.size,
                            segmentStatus = defaultSegmentStatus()
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            segmentStatus = "Failed to load local segments"
                        )
                    }
                }
        }

        // Start live preview only if there is no active recording.
        if (recordingManager.recordingState.value !is RecordingState.Recording) {
            startPreview()
        }

        // Observe recording state
        recordingStateJob?.cancel()
        recordingStateJob = viewModelScope.launch {
            recordingManager.recordingState.collect { state ->
                when (state) {
                    is RecordingState.Idle -> {
                        isStartingRecording = false
                        isAutoStopInProgress = false
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true
                            )
                        }
                        if (!_uiState.value.isRecording) {
                            updateSegmentStatus(defaultSegmentStatus())
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
                                isPreviewActive = false,
                                segmentStatus = if (activeAutoSegment != null) {
                                    "Auto recording active on local segment"
                                } else {
                                    "Manual recording active"
                                }
                            )
                        }
                        maybeAutoStopAtSegmentEnd(state)
                    }
                    is RecordingState.Processing -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = true,
                                canStartRecording = false,
                                segmentStatus = "Processing run data..."
                            )
                        }
                    }
                    is RecordingState.Completed -> {
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true,
                                completedRunId = state.runId,
                                segmentStatus = defaultSegmentStatus()
                            )
                        }
                    }
                    is RecordingState.Error -> {
                        isStartingRecording = false
                        isAutoStopInProgress = false
                        activeAutoSegment = null
                        stopForegroundRecordingService()
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true,
                                error = state.message,
                                segmentStatus = defaultSegmentStatus()
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
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        startCandidateSegment = null
        startCandidateHits = 0

        // Stop preview before starting real recording
        stopPreview()
        startForegroundRecordingService(_uiState.value.trackId)
        _uiState.update {
            it.copy(
                canStartRecording = false,
                completedRunId = null,
                error = null,
                segmentStatus = if (autoSegment != null) {
                    "Segment detected: auto recording started"
                } else {
                    "Manual recording started"
                }
            )
        }

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
                stopForegroundRecordingService()
                _uiState.update {
                    it.copy(
                        canStartRecording = true,
                        error = e.message,
                        segmentStatus = defaultSegmentStatus()
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
        _uiState.update {
            it.copy(
                isProcessing = true,
                canStartRecording = false,
                segmentStatus = "Processing run data..."
            )
        }

        viewModelScope.launch {
            val captureHandle = recordingManager.stopRecording()

            if (captureHandle == null) {
                isAutoStopInProgress = false
                activeAutoSegment = null
                finishRecordingSession(resetToPreview = true)
                return@launch
            }

            _uiState.update { it.copy(isProcessing = true) }

            processRunUseCase(captureHandle)
                .onSuccess { runId ->
                    isAutoStopInProgress = false
                    activeAutoSegment = null
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            canStartRecording = true,
                            completedRunId = runId,
                            segmentStatus = defaultSegmentStatus()
                        )
                    }
                    finishRecordingSession(resetToPreview = false)
                }
                .onFailure { e ->
                    isAutoStopInProgress = false
                    activeAutoSegment = null
                    _uiState.update { 
                        it.copy(
                            isProcessing = false,
                            canStartRecording = true,
                            error = e.message
                        )
                    }
                    finishRecordingSession(resetToPreview = true)
                }
        }
    }

    fun consumeCompletedRun() {
        _uiState.update { it.copy(completedRunId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun maybeAutoStartAtSegmentStart(location: PreviewLocation) {
        val currentState = _uiState.value
        if (currentState.isRecording || currentState.isProcessing) {
            updateLastPreviewLocation(location)
            return
        }
        if (localSegments.isEmpty()) {
            updateSegmentStatus(defaultSegmentStatus())
            updateLastPreviewLocation(location)
            return
        }
        if (location.accuracy <= 0f || location.accuracy > MAX_PREVIEW_ACCURACY_M) {
            resetStartCandidate()
            updateSegmentStatus("Waiting for good GPS to arm segments")
            updateLastPreviewLocation(location)
            return
        }
        if (location.speed < AUTO_START_MIN_SPEED_MPS) {
            resetStartCandidate()
            updateSegmentStatus("Move faster to arm segment auto-start")
            updateLastPreviewLocation(location)
            return
        }

        val now = System.currentTimeMillis()
        if (now < autoCooldownUntilMs) {
            updateSegmentStatus("Auto-start cooldown active")
            updateLastPreviewLocation(location)
            return
        }

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

        if (closestSegment == null || closestDistance > SEGMENT_START_ARM_RADIUS_M) {
            resetStartCandidate()
            if (closestDistance.isFinite()) {
                updateSegmentStatus("Nearest segment start: ${formatMeters(closestDistance)}")
            }
            updateLastPreviewLocation(location)
            return
        }

        if (startCandidateSegment?.id != closestSegment.id) {
            startCandidateSegment = closestSegment
            startCandidateHits = 0
        }

        if (closestDistance > SEGMENT_START_RELEASE_RADIUS_M) {
            startCandidateHits = 0
            updateSegmentStatus("Approaching segment: ${formatMeters(closestDistance)}")
            updateLastPreviewLocation(location)
            return
        }

        val movingInDirection = isMovingInSegmentDirection(
            previousLat = lastPreviewLatitude,
            previousLon = lastPreviewLongitude,
            currentLat = location.latitude,
            currentLon = location.longitude,
            segment = closestSegment
        )

        if (closestDistance <= SEGMENT_START_TRIGGER_RADIUS_M && movingInDirection) {
            startCandidateHits++
            updateSegmentStatus(
                "Segment armed ($startCandidateHits/$MIN_START_CONFIRMATION_HITS) at ${formatMeters(closestDistance)}"
            )
        } else {
            startCandidateHits = max(0, startCandidateHits - 1)
            updateSegmentStatus("Align with segment direction to auto-start")
        }

        if (startCandidateHits >= MIN_START_CONFIRMATION_HITS) {
            autoCooldownUntilMs = now + AUTO_TRIGGER_COOLDOWN_MS
            startRecordingInternal(autoSegment = closestSegment)
            resetStartCandidate()
        }

        updateLastPreviewLocation(location)
    }

    private fun maybeAutoStopAtSegmentEnd(state: RecordingState.Recording) {
        if (isAutoStopInProgress) return
        val segment = activeAutoSegment ?: return
        val latitude = state.currentLatitude ?: return
        val longitude = state.currentLongitude ?: return

        val elapsedMs = System.currentTimeMillis() - startTime
        if (elapsedMs < MIN_RECORDING_BEFORE_AUTO_STOP_MS) return

        val movingInDirection = isMovingInSegmentDirection(
            previousLat = lastRecordingLatitude,
            previousLon = lastRecordingLongitude,
            currentLat = latitude,
            currentLon = longitude,
            segment = segment
        )
        updateLastRecordingLocation(latitude, longitude)
        if (!movingInDirection) return

        val distanceToEnd = haversineMeters(
            lat1 = latitude,
            lon1 = longitude,
            lat2 = segment.end.lat,
            lon2 = segment.end.lon
        )
        updateSegmentStatus("Auto recording: ${formatMeters(distanceToEnd)} to segment end")
        val previousDistance = lastDistanceToSegmentEndM
        lastDistanceToSegmentEndM = distanceToEnd

        if (previousDistance != null && distanceToEnd > previousDistance + 6.0) return

        val startToEndDistance = haversineMeters(
            lat1 = segment.start.lat,
            lon1 = segment.start.lon,
            lat2 = segment.end.lat,
            lon2 = segment.end.lon
        )
        val distanceFromStart = haversineMeters(
            lat1 = latitude,
            lon1 = longitude,
            lat2 = segment.start.lat,
            lon2 = segment.start.lon
        )
        val minProgressFromStart = min(startToEndDistance * 0.35, 120.0)
        if (distanceFromStart < minProgressFromStart) return

        if (distanceToEnd <= SEGMENT_END_RADIUS_M) {
            isAutoStopInProgress = true
            autoCooldownUntilMs = System.currentTimeMillis() + AUTO_TRIGGER_COOLDOWN_MS
            activeAutoSegment = null
            updateSegmentStatus("Segment end reached. Stopping recording...")
            stopRecordingInternal()
        }
    }

    private fun resetStartCandidate() {
        startCandidateSegment = null
        startCandidateHits = 0
    }

    private fun updateLastPreviewLocation(location: PreviewLocation) {
        lastPreviewLatitude = location.latitude
        lastPreviewLongitude = location.longitude
    }

    private fun updateLastRecordingLocation(latitude: Double, longitude: Double) {
        lastRecordingLatitude = latitude
        lastRecordingLongitude = longitude
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

    private fun finishRecordingSession(resetToPreview: Boolean) {
        recordingManager.cancelRecording()
        stopForegroundRecordingService()
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        isStartingRecording = false
        _uiState.update {
            it.copy(
                isRecording = false,
                isProcessing = false,
                canStartRecording = true
            )
        }

        if (resetToPreview && !_uiState.value.isRecording) {
            startPreview()
            updateSegmentStatus(defaultSegmentStatus())
        }
    }

    private fun startForegroundRecordingService(trackId: String) {
        runCatching {
            val intent = Intent(appContext, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_FOREGROUND
                putExtra(RecordingService.EXTRA_TRACK_ID, trackId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { error ->
            _uiState.update { it.copy(error = error.message ?: "Failed to start recording service") }
        }
    }

    private fun stopForegroundRecordingService() {
        runCatching {
            val intent = Intent(appContext, RecordingService::class.java).apply {
                action = RecordingService.ACTION_STOP_FOREGROUND
            }
            appContext.startService(intent)
        }
    }

    private fun defaultSegmentStatus(): String {
        return if (localSegments.isEmpty()) {
            "No local segments yet. Record a run to enable auto-segments."
        } else {
            "Auto-segments enabled (${localSegments.size} loaded)"
        }
    }

    private fun updateSegmentStatus(message: String) {
        _uiState.update { state ->
            if (state.segmentStatus == message) state else state.copy(segmentStatus = message)
        }
    }

    private fun formatMeters(distanceM: Double): String {
        return String.format(Locale.US, "%.0f m", distanceM)
    }

    private fun isMovingInSegmentDirection(
        previousLat: Double?,
        previousLon: Double?,
        currentLat: Double,
        currentLon: Double,
        segment: TrackSegment
    ): Boolean {
        if (previousLat == null || previousLon == null) return true

        val (moveX, moveY) = projectDeltaMeters(previousLat, previousLon, currentLat, currentLon)
        val moveNorm = sqrt(moveX * moveX + moveY * moveY)
        if (moveNorm < 1.0) return true

        val (segmentX, segmentY) = projectDeltaMeters(
            segment.start.lat,
            segment.start.lon,
            segment.end.lat,
            segment.end.lon
        )
        val segmentNorm = sqrt(segmentX * segmentX + segmentY * segmentY)
        if (segmentNorm < 1.0) return true

        val dot = (moveX * segmentX + moveY * segmentY) / (moveNorm * segmentNorm)
        return dot >= MIN_DIRECTION_DOT
    }

    private fun projectDeltaMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Pair<Double, Double> {
        val avgLatRad = Math.toRadians((fromLat + toLat) / 2.0)
        val metersPerDegLat = 111_132.0
        val metersPerDegLon = 111_320.0 * cos(avgLatRad)
        val dx = (toLon - fromLon) * metersPerDegLon
        val dy = (toLat - fromLat) * metersPerDegLat
        return dx to dy
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
        recordingStateJob?.cancel()
        stopPreview()
        val state = recordingManager.recordingState.value
        if (state is RecordingState.Processing) {
            recordingManager.cancelRecording()
            stopForegroundRecordingService()
        }
    }
}
