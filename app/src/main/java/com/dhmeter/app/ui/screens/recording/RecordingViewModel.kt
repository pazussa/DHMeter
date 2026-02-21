package com.dropindh.app.ui.screens.recording

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropindh.app.monetization.EventTracker
import com.dhmeter.domain.model.SensorSensitivitySettings
import com.dropindh.app.service.RecordingService
import com.dropindh.app.ui.i18n.tr
import com.dhmeter.domain.model.TrackSegment
import com.dhmeter.domain.usecase.GetTrackByIdUseCase
import com.dhmeter.domain.usecase.GetTrackSegmentsUseCase
import com.dhmeter.domain.usecase.ProcessRunUseCase
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.domain.repository.TrackAutoStartRepository
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState
import com.dhmeter.sensing.preview.PreviewLocation
import com.dhmeter.sensing.preview.RecordingPreviewManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
    val segmentStatus: String = "",
    // Live metrics (0-1 normalized)
    val liveImpact: Float = 0f,
    val liveHarshness: Float = 0f,
    val liveStability: Float = 0f,
    // Preview mode
    val isPreviewActive: Boolean = false,
    // Recovery action when processing appears stuck
    val canRecoverFromProcessing: Boolean = false,
    val manualStartCountdownSeconds: Int = 0,
    val isAutoStartEnabled: Boolean = false,
    val sensitivitySettings: SensorSensitivitySettings = SensorSensitivitySettings()
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val getTrackSegmentsUseCase: GetTrackSegmentsUseCase,
    private val recordingManager: RecordingManager,
    private val processRunUseCase: ProcessRunUseCase,
    private val previewManager: RecordingPreviewManager,
    private val sensitivityRepository: SensorSensitivityRepository,
    private val trackAutoStartRepository: TrackAutoStartRepository,
    private val eventTracker: EventTracker
) : ViewModel() {

    companion object {
        private const val PREVIEW_CLIENT_ID = "recording_screen"
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
        private const val MANUAL_START_DELAY_SECONDS = 10
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
    private var trackLoadJob: Job? = null
    private var segmentLoadJob: Job? = null
    private var processingRecoveryJob: Job? = null
    private var manualStartDelayJob: Job? = null
    private var isManualStartForegroundArmed: Boolean = false
    private var isPreviewForegroundArmed: Boolean = false
    private var startCandidateSegment: TrackSegment? = null
    private var startCandidateHits: Int = 0
    private var lastPreviewLatitude: Double? = null
    private var lastPreviewLongitude: Double? = null
    private var lastRecordingLatitude: Double? = null
    private var lastRecordingLongitude: Double? = null
    private var lastDistanceToSegmentEndM: Double? = null

    init {
        viewModelScope.launch {
            sensitivityRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(sensitivitySettings = settings)
                }
            }
        }
    }

    fun initialize(trackId: String) {
        trackLoadJob?.cancel()
        segmentLoadJob?.cancel()
        processingRecoveryJob?.cancel()
        manualStartDelayJob?.cancel()
        manualStartDelayJob = null
        localSegments = emptyList()
        activeAutoSegment = null
        isAutoStopInProgress = false
        isStartingRecording = false
        startCandidateSegment = null
        startCandidateHits = 0
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        val isTrackAutoStartEnabled = trackAutoStartRepository.isAutoStartEnabled(trackId)

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
                segmentStatus = msgLoadingSegments(),
                canRecoverFromProcessing = false,
                manualStartCountdownSeconds = 0,
                isAutoStartEnabled = isTrackAutoStartEnabled
            )
        }

        if (recordingManager.recordingState.value is RecordingState.Processing) {
            recordingManager.cancelRecording()
        }
        
        trackLoadJob = viewModelScope.launch {
            getTrackByIdUseCase(trackId)
                .onSuccess { track ->
                    _uiState.update { it.copy(trackName = track.name) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }

        segmentLoadJob = viewModelScope.launch {
            loadLocalSegments(trackId, showLoadingStatus = true)
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
                        processingRecoveryJob?.cancel()
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true,
                                canRecoverFromProcessing = false,
                                manualStartCountdownSeconds = 0
                            )
                        }
                        if (!_uiState.value.isRecording) {
                            updateSegmentStatus(defaultSegmentStatus())
                        }
                    }
                    is RecordingState.Recording -> {
                        isStartingRecording = false
                        processingRecoveryJob?.cancel()
                        _uiState.update { 
                            it.copy(
                                isRecording = true,
                                isProcessing = false,
                                canStartRecording = false,
                                gpsSignal = mapGpsSignal(
                                    state.gpsAccuracy,
                                    _uiState.value.sensitivitySettings.gpsSensitivity
                                ),
                                movementDetected = state.movementDetected,
                                signalQuality = mapSignalQuality(state.signalStability),
                                currentSpeed = state.currentSpeed,
                                liveImpact = state.liveImpact,
                                liveHarshness = state.liveHarshness,
                                liveStability = state.liveStability,
                                isPreviewActive = false,
                                canRecoverFromProcessing = false,
                                manualStartCountdownSeconds = 0,
                                segmentStatus = if (activeAutoSegment != null) {
                                    msgAutoRecordingOnSegment()
                                } else {
                                    msgManualRecordingActive()
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
                                canRecoverFromProcessing = false,
                                manualStartCountdownSeconds = 0,
                                segmentStatus = msgProcessingRunData()
                            )
                        }
                        armProcessingRecovery()
                    }
                    is RecordingState.Completed -> {
                        processingRecoveryJob?.cancel()
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true,
                                completedRunId = state.runId,
                                canRecoverFromProcessing = false,
                                manualStartCountdownSeconds = 0,
                                segmentStatus = defaultSegmentStatus()
                            )
                        }
                    }
                    is RecordingState.Error -> {
                        isStartingRecording = false
                        isAutoStopInProgress = false
                        activeAutoSegment = null
                        processingRecoveryJob?.cancel()
                        stopForegroundRecordingServiceOrKeepAutoMonitoring()
                        _uiState.update { 
                            it.copy(
                                isRecording = false,
                                isProcessing = false,
                                canStartRecording = true,
                                error = state.message,
                                canRecoverFromProcessing = false,
                                manualStartCountdownSeconds = 0,
                                segmentStatus = defaultSegmentStatus()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startPreview() {
        armForegroundForPreview()
        val started = previewManager.startPreview(
            clientId = PREVIEW_CLIENT_ID,
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
        if (!started) {
            disarmForegroundForPreview(forceStop = true)
            return
        }

        _uiState.update { it.copy(isPreviewActive = true) }
    }
    
    private fun stopPreview(keepForeground: Boolean = false) {
        previewManager.stopPreview(PREVIEW_CLIENT_ID)
        _uiState.update { it.copy(isPreviewActive = false) }
        if (!keepForeground) {
            disarmForegroundForPreview(forceStop = false)
        }
    }

    fun startRecording() {
        recoverFromStaleProcessingStateIfNeeded()
        if (_uiState.value.isRecording || _uiState.value.isProcessing || isStartingRecording) return
        if (manualStartDelayJob?.isActive == true) return

        if (!armForegroundForManualStart()) {
            return
        }

        manualStartDelayJob = viewModelScope.launch {
            try {
                for (remaining in MANUAL_START_DELAY_SECONDS downTo 1) {
                    _uiState.update {
                        it.copy(
                            canStartRecording = false,
                            completedRunId = null,
                            error = null,
                            manualStartCountdownSeconds = remaining,
                            segmentStatus = msgManualStartCountdown(remaining)
                        )
                    }
                    delay(1000)
                }

                _uiState.update { it.copy(manualStartCountdownSeconds = 0) }
                val started = startRecordingInternal(
                    autoSegment = null,
                    foregroundAlreadyArmed = true
                )
                if (!started && isManualStartForegroundArmed) {
                    disarmForegroundForManualStart()
                }
            } catch (_: CancellationException) {
                if (isManualStartForegroundArmed) {
                    disarmForegroundForManualStart()
                }
                _uiState.update { state ->
                    if (state.isRecording || state.isProcessing) {
                        state
                    } else {
                        state.copy(
                            canStartRecording = true,
                            manualStartCountdownSeconds = 0,
                            segmentStatus = defaultSegmentStatus()
                        )
                    }
                }
            } finally {
                manualStartDelayJob = null
            }
        }
    }

    private fun startRecordingInternal(
        autoSegment: TrackSegment?,
        foregroundAlreadyArmed: Boolean = false
    ): Boolean {
        recoverFromStaleProcessingStateIfNeeded()
        if (_uiState.value.isRecording || _uiState.value.isProcessing || isStartingRecording) return false

        activeAutoSegment = autoSegment
        isAutoStopInProgress = false
        isStartingRecording = true
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        startCandidateSegment = null
        startCandidateHits = 0

        // Stop preview before starting real recording
        stopPreview(keepForeground = true)
        previewManager.pauseAll()
        if (!foregroundAlreadyArmed && !startForegroundRecordingService(_uiState.value.trackId)) {
            isStartingRecording = false
            isAutoStopInProgress = false
            activeAutoSegment = null
            previewManager.resumeAll()
            _uiState.update {
                it.copy(
                    canStartRecording = true,
                    segmentStatus = defaultSegmentStatus()
                )
            }
            startPreview()
            return false
        }
        isManualStartForegroundArmed = false
        _uiState.update {
            it.copy(
                canStartRecording = false,
                completedRunId = null,
                error = null,
                segmentStatus = if (autoSegment != null) {
                    msgSegmentDetectedAutoStarted()
                } else {
                    msgManualRecordingStarted()
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
                previewManager.resumeAll()
                stopForegroundRecordingServiceOrKeepAutoMonitoring()
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
        return true
    }

    fun stopRecording() {
        manualStartDelayJob?.cancel()
        manualStartDelayJob = null
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
                canRecoverFromProcessing = false,
                segmentStatus = msgProcessingRunData()
            )
        }
        armProcessingRecovery()

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
                    eventTracker.trackFirstRunCompleteIfNeeded()
                    isAutoStopInProgress = false
                    activeAutoSegment = null
                    segmentLoadJob?.cancel()
                    segmentLoadJob = viewModelScope.launch {
                        loadLocalSegments(_uiState.value.trackId, showLoadingStatus = false)
                    }
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

    fun updateImpactSensitivity(value: Float) {
        viewModelScope.launch {
            sensitivityRepository.updateImpactSensitivity(value)
        }
    }

    fun updateHarshnessSensitivity(value: Float) {
        viewModelScope.launch {
            sensitivityRepository.updateHarshnessSensitivity(value)
        }
    }

    fun updateStabilitySensitivity(value: Float) {
        viewModelScope.launch {
            sensitivityRepository.updateStabilitySensitivity(value)
        }
    }

    fun updateGpsSensitivity(value: Float) {
        viewModelScope.launch {
            sensitivityRepository.updateGpsSensitivity(value)
        }
    }

    fun resetSensitivityDefaults() {
        viewModelScope.launch {
            sensitivityRepository.resetToDefaults()
        }
    }

    fun resetStuckProcessing() {
        manualStartDelayJob?.cancel()
        manualStartDelayJob = null
        if (!_uiState.value.isProcessing) return
        processingRecoveryJob?.cancel()
        isStartingRecording = false
        isAutoStopInProgress = false
        activeAutoSegment = null
        timerJob?.cancel()
        recordingManager.cancelRecording()
        stopForegroundRecordingService()
        _uiState.update {
            it.copy(
                isRecording = false,
                isProcessing = false,
                canStartRecording = true,
                canRecoverFromProcessing = false,
                error = null,
                manualStartCountdownSeconds = 0,
                segmentStatus = defaultSegmentStatus()
            )
        }
        startPreview()
    }

    private fun maybeAutoStartAtSegmentStart(location: PreviewLocation) {
        val currentState = _uiState.value
        if (currentState.isRecording || currentState.isProcessing) {
            updateLastPreviewLocation(location)
            return
        }
        if (manualStartDelayJob?.isActive == true) {
            updateLastPreviewLocation(location)
            return
        }
        if (!currentState.isAutoStartEnabled) {
            updateSegmentStatus(msgAutoStartDisabled())
            updateLastPreviewLocation(location)
            return
        }
        if (localSegments.isEmpty()) {
            updateSegmentStatus(defaultSegmentStatus())
            updateLastPreviewLocation(location)
            return
        }
        val gpsSensitivity = currentState.sensitivitySettings.gpsSensitivity
        val maxPreviewAccuracy = (MAX_PREVIEW_ACCURACY_M / gpsSensitivity.coerceAtLeast(0.01f))
            .coerceIn(10f, 60f)

        if (location.accuracy <= 0f || location.accuracy > maxPreviewAccuracy) {
            resetStartCandidate()
            updateSegmentStatus(msgWaitingForGoodGps())
            updateLastPreviewLocation(location)
            return
        }
        if (location.speed < AUTO_START_MIN_SPEED_MPS) {
            resetStartCandidate()
            updateSegmentStatus(msgMoveFasterToArm())
            updateLastPreviewLocation(location)
            return
        }

        val now = System.currentTimeMillis()
        if (now < autoCooldownUntilMs) {
            updateSegmentStatus(msgAutoStartCooldown())
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
                updateSegmentStatus(msgNearestSegment(formatMeters(closestDistance)))
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
            updateSegmentStatus(msgApproachingSegment(formatMeters(closestDistance)))
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
                msgSegmentArmed(
                    startCandidateHits,
                    MIN_START_CONFIRMATION_HITS,
                    formatMeters(closestDistance)
                )
            )
        } else {
            startCandidateHits = max(0, startCandidateHits - 1)
            updateSegmentStatus(msgAlignWithSegmentDirection())
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
        updateSegmentStatus(msgAutoRecordingToEnd(formatMeters(distanceToEnd)))
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
            updateSegmentStatus(msgSegmentEndStopping())
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
        processingRecoveryJob?.cancel()
        recordingManager.cancelRecording()
        previewManager.resumeAll()
        stopForegroundRecordingServiceOrKeepAutoMonitoring()
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        isStartingRecording = false
        _uiState.update {
            it.copy(
                isRecording = false,
                isProcessing = false,
                canStartRecording = true,
                canRecoverFromProcessing = false
            )
        }

        if (resetToPreview && !_uiState.value.isRecording) {
            startPreview()
            updateSegmentStatus(defaultSegmentStatus())
        }
    }

    private fun startForegroundRecordingService(trackId: String): Boolean {
        val started = runCatching {
            val intent = Intent(appContext, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_FOREGROUND
                putExtra(RecordingService.EXTRA_TRACK_ID, trackId)
            }
            appContext.startForegroundService(intent)
        }.onFailure { error ->
            _uiState.update { it.copy(error = error.message ?: msgFailedStartService()) }
        }.isSuccess
        return started
    }

    private fun stopForegroundRecordingService() {
        isManualStartForegroundArmed = false
        isPreviewForegroundArmed = false
        runCatching {
            val intent = Intent(appContext, RecordingService::class.java).apply {
                action = RecordingService.ACTION_STOP_FOREGROUND
            }
            appContext.startService(intent)
        }
    }

    private fun keepAutoMonitoringForegroundIfEnabled(): Boolean {
        val currentTrackId = _uiState.value.trackId
        val targetTrackId = when {
            currentTrackId.isNotBlank() && trackAutoStartRepository.isAutoStartEnabled(currentTrackId) -> currentTrackId
            else -> trackAutoStartRepository.getEnabledTrackIds().firstOrNull()
        } ?: return false

        return runCatching {
            val intent = Intent(appContext, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_FOREGROUND
                putExtra(RecordingService.EXTRA_TRACK_ID, targetTrackId)
            }
            appContext.startForegroundService(intent)
        }.isSuccess
    }

    private fun stopForegroundRecordingServiceOrKeepAutoMonitoring(forceStop: Boolean = false) {
        isManualStartForegroundArmed = false
        isPreviewForegroundArmed = false
        if (!forceStop && keepAutoMonitoringForegroundIfEnabled()) {
            return
        }
        stopForegroundRecordingService()
    }

    private fun armForegroundForPreview() {
        if (isPreviewForegroundArmed || _uiState.value.trackId.isBlank()) return
        if (startForegroundRecordingService(_uiState.value.trackId)) {
            isPreviewForegroundArmed = true
        }
    }

    private fun disarmForegroundForPreview(forceStop: Boolean) {
        if (!isPreviewForegroundArmed) return
        isPreviewForegroundArmed = false
        if (forceStop || (!isManualStartForegroundArmed && !_uiState.value.isRecording && !_uiState.value.isProcessing)) {
            stopForegroundRecordingServiceOrKeepAutoMonitoring(forceStop = forceStop)
        }
    }

    private fun armForegroundForManualStart(): Boolean {
        if (isPreviewForegroundArmed) {
            isManualStartForegroundArmed = false
            return true
        }
        val started = startForegroundRecordingService(_uiState.value.trackId)
        isManualStartForegroundArmed = started
        return started
    }

    private fun disarmForegroundForManualStart() {
        if (!isManualStartForegroundArmed) return
        isManualStartForegroundArmed = false
        if (!isPreviewForegroundArmed && !_uiState.value.isRecording && !_uiState.value.isProcessing) {
            stopForegroundRecordingServiceOrKeepAutoMonitoring()
        }
    }

    private fun defaultSegmentStatus(): String {
        return if (!_uiState.value.isAutoStartEnabled) {
            msgAutoStartDisabled()
        } else if (localSegments.isEmpty()) {
            msgNoLocalSegments()
        } else {
            msgAutoSegmentsEnabled(localSegments.size)
        }
    }

    private suspend fun loadLocalSegments(trackId: String, showLoadingStatus: Boolean) {
        if (showLoadingStatus) {
            updateSegmentStatus(msgLoadingSegments())
        }

        getTrackSegmentsUseCase(trackId)
            .onSuccess { segments ->
                localSegments = segments.sortedByDescending { it.startedAt }
                _uiState.update { state ->
                    state.copy(
                        segmentCount = localSegments.size,
                        segmentStatus = if (state.isRecording || state.isProcessing) {
                            state.segmentStatus
                        } else {
                            defaultSegmentStatus()
                        }
                    )
                }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(
                        error = e.message,
                        segmentStatus = msgFailedLoadSegments()
                    )
                }
            }
    }

    private fun recoverFromStaleProcessingStateIfNeeded() {
        if (_uiState.value.isProcessing && recordingManager.recordingState.value !is RecordingState.Processing) {
            processingRecoveryJob?.cancel()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    canStartRecording = true,
                    canRecoverFromProcessing = false,
                    segmentStatus = defaultSegmentStatus()
                )
            }
        }
    }

    private fun armProcessingRecovery() {
        processingRecoveryJob?.cancel()
        processingRecoveryJob = viewModelScope.launch {
            delay(12_000)
            if (_uiState.value.isProcessing) {
                _uiState.update { state ->
                    state.copy(canRecoverFromProcessing = true)
                }
                updateSegmentStatus(msgProcessingTakingLonger())
            }
        }
    }

    private fun msgLoadingSegments(): String =
        tr(appContext, "Loading local segments...", "Cargando segmentos locales...")

    private fun msgAutoRecordingOnSegment(): String =
        tr(appContext, "Auto recording active on local segment", "Grabación automática activa en segmento local")

    private fun msgManualRecordingActive(): String =
        tr(appContext, "Manual recording active", "Grabación manual activa")

    private fun msgProcessingRunData(): String =
        tr(appContext, "Processing run data...", "Procesando datos de la bajada...")

    private fun msgManualStartCountdown(remainingSeconds: Int): String =
        tr(
            appContext,
            "Manual start in ${remainingSeconds}s...",
            "Inicio manual en ${remainingSeconds}s..."
        )

    private fun msgSegmentDetectedAutoStarted(): String =
        tr(
            appContext,
            "Segment detected: auto recording started",
            "Segmento detectado: grabación automática iniciada"
        )

    private fun msgManualRecordingStarted(): String =
        tr(appContext, "Manual recording started", "Grabación manual iniciada")

    private fun msgWaitingForGoodGps(): String =
        tr(appContext, "Waiting for good GPS to arm segments", "Esperando buen GPS para activar segmentos")

    private fun msgMoveFasterToArm(): String =
        tr(appContext, "Move faster to arm segment auto-start", "Muévete más rápido para activar el auto-inicio")

    private fun msgAutoStartCooldown(): String =
        tr(appContext, "Auto-start cooldown active", "En enfriamiento de auto-inicio")

    private fun msgNearestSegment(distance: String): String =
        tr(appContext, "Nearest segment start: $distance", "Inicio de segmento más cercano: $distance")

    private fun msgApproachingSegment(distance: String): String =
        tr(appContext, "Approaching segment: $distance", "Acercándose al segmento: $distance")

    private fun msgSegmentArmed(hits: Int, requiredHits: Int, distance: String): String =
        tr(
            appContext,
            "Segment armed ($hits/$requiredHits) at $distance",
            "Segmento activado ($hits/$requiredHits) a $distance"
        )

    private fun msgAlignWithSegmentDirection(): String =
        tr(appContext, "Align with segment direction to auto-start", "Alinéate con la dirección del segmento para auto-iniciar")

    private fun msgAutoRecordingToEnd(distance: String): String =
        tr(appContext, "Auto recording: $distance to segment end", "Grabación automática: $distance para fin de segmento")

    private fun msgSegmentEndStopping(): String =
        tr(appContext, "Segment end reached. Stopping recording...", "Fin de segmento alcanzado. Deteniendo grabación...")

    private fun msgFailedStartService(): String =
        tr(appContext, "Failed to start recording service", "No se pudo iniciar el servicio de grabación")

    private fun msgAutoStartDisabled(): String =
        tr(
            appContext,
            "Auto-start is disabled for this track.",
            "El auto-start está desactivado para este track."
        )

    private fun msgNoLocalSegments(): String =
        tr(
            appContext,
            "No local segments yet. Record a run to enable auto-segments.",
            "Aún no hay segmentos locales. Graba una bajada para habilitarlos."
        )

    private fun msgAutoSegmentsEnabled(count: Int): String =
        tr(appContext, "Auto-segments enabled ($count loaded)", "Auto-segmentos habilitados ($count cargados)")

    private fun msgFailedLoadSegments(): String =
        tr(appContext, "Failed to load local segments", "No se pudieron cargar los segmentos locales")

    private fun msgProcessingTakingLonger(): String =
        tr(
            appContext,
            "Processing is taking longer than expected",
            "El procesamiento está tardando más de lo esperado"
        )

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

    private fun mapGpsSignal(accuracy: Float, gpsSensitivity: Float): GpsSignalLevel {
        val normalizedSensitivity = gpsSensitivity.coerceAtLeast(0.01f)
        val goodThreshold = (5f / normalizedSensitivity).coerceIn(2f, 15f)
        val mediumThreshold = (15f / normalizedSensitivity).coerceIn(5f, 40f)

        return when {
            accuracy < 0 -> GpsSignalLevel.NONE
            accuracy <= goodThreshold -> GpsSignalLevel.GOOD
            accuracy <= mediumThreshold -> GpsSignalLevel.MEDIUM
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
        processingRecoveryJob?.cancel()
        manualStartDelayJob?.cancel()
        manualStartDelayJob = null
        stopPreview()
        val state = recordingManager.recordingState.value
        if (state is RecordingState.Processing) {
            recordingManager.cancelRecording()
            stopForegroundRecordingService()
        }
    }
}
