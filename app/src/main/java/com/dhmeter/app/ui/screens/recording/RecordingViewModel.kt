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
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState
import com.dhmeter.sensing.preview.RecordingPreviewManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
    private val eventTracker: EventTracker
) : ViewModel() {

    companion object {
        private const val PREVIEW_CLIENT_ID = "recording_screen"
        private const val MANUAL_START_DELAY_SECONDS = 10
    }

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var startTime: Long = 0
    private var localSegments: List<TrackSegment> = emptyList()
    private var isStartingRecording: Boolean = false
    private var recordingStateJob: Job? = null
    private var trackLoadJob: Job? = null
    private var segmentLoadJob: Job? = null
    private var processingRecoveryJob: Job? = null
    private var manualStartDelayJob: Job? = null
    private var isManualStartForegroundArmed: Boolean = false
    private var isPreviewForegroundArmed: Boolean = false

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
        isStartingRecording = false

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
                manualStartCountdownSeconds = 0
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
                                segmentStatus = msgManualRecordingActive()
                            )
                        }
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
                        processingRecoveryJob?.cancel()
                        stopForegroundRecordingService()
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
            onLocation = { _ -> }
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
                val started = startRecordingInternal(foregroundAlreadyArmed = true)
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
        foregroundAlreadyArmed: Boolean = false
    ): Boolean {
        recoverFromStaleProcessingStateIfNeeded()
        if (_uiState.value.isRecording || _uiState.value.isProcessing || isStartingRecording) return false

        isStartingRecording = true

        // Stop preview before starting real recording
        stopPreview(keepForeground = true)
        previewManager.pauseAll()
        if (!foregroundAlreadyArmed && !startForegroundRecordingService(_uiState.value.trackId)) {
            isStartingRecording = false
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
                segmentStatus = msgManualRecordingStarted()
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
                previewManager.resumeAll()
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
        return true
    }

    fun stopRecording() {
        manualStartDelayJob?.cancel()
        manualStartDelayJob = null
        isStartingRecording = false
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
                finishRecordingSession(resetToPreview = true)
                return@launch
            }

            _uiState.update { it.copy(isProcessing = true) }

            processRunUseCase(captureHandle)
                .onSuccess { runId ->
                    eventTracker.trackFirstRunCompleteIfNeeded()
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

    fun updateEventSensitivity(value: Float) {
        viewModelScope.launch {
            sensitivityRepository.updateEventSensitivity(value)
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
        stopForegroundRecordingService()
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
            stopForegroundRecordingService()
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
            stopForegroundRecordingService()
        }
    }

    private fun defaultSegmentStatus(): String {
        return if (localSegments.isEmpty()) {
            msgNoLocalSegments()
        } else {
            msgLocalSegmentsAvailable(localSegments.size)
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

    private fun msgManualRecordingStarted(): String =
        tr(appContext, "Manual recording started", "Grabación manual iniciada")

    private fun msgFailedStartService(): String =
        tr(appContext, "Failed to start recording service", "No se pudo iniciar el servicio de grabación")

    private fun msgNoLocalSegments(): String =
        tr(
            appContext,
            "No local segments yet.",
            "Aún no hay segmentos locales."
        )

    private fun msgLocalSegmentsAvailable(count: Int): String =
        tr(appContext, "Local segments available ($count loaded)", "Segmentos locales disponibles ($count cargados)")

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
