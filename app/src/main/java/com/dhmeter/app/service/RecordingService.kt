package com.dropindh.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dropindh.app.DHMeterApplication
import com.dropindh.app.R
import com.dropindh.app.ui.MainActivity
import com.dropindh.app.ui.i18n.tr
import com.dhmeter.domain.model.TrackSegment
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.domain.usecase.GetTrackSegmentsUseCase
import com.dhmeter.domain.usecase.ProcessRunUseCase
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState as SensorRecordingState
import com.dhmeter.sensing.preview.PreviewLocation
import com.dhmeter.sensing.preview.RecordingPreviewManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Foreground service for recording and background auto-start monitoring.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = DHMeterApplication.CHANNEL_RECORDING
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.dhmeter.action.START_RECORDING"
        const val ACTION_START_FOREGROUND = "com.dhmeter.action.START_FOREGROUND"
        const val ACTION_STOP = "com.dhmeter.action.STOP_RECORDING"
        const val ACTION_STOP_FOREGROUND = "com.dhmeter.action.STOP_FOREGROUND"
        const val EXTRA_TRACK_ID = "track_id"

        private const val AUTO_PREVIEW_CLIENT_ID = "recording_service"
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
        private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L
    }

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var processRunUseCase: ProcessRunUseCase

    @Inject
    lateinit var getTrackSegmentsUseCase: GetTrackSegmentsUseCase

    @Inject
    lateinit var previewManager: RecordingPreviewManager

    @Inject
    lateinit var sensitivityRepository: SensorSensitivityRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = RecordingBinder()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var currentTrackId: String? = null
    private var autoEnabledTrackId: String? = null
    private var autoSegments: List<TrackSegment> = emptyList()

    private var startTimeMs: Long = 0
    private var isForegroundActive: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeAutoSegment: TrackSegment? = null
    private var autoCooldownUntilMs: Long = 0L
    private var isAutoStopInProgress: Boolean = false

    private var startCandidateSegment: TrackSegment? = null
    private var startCandidateHits: Int = 0
    private var lastPreviewLatitude: Double? = null
    private var lastPreviewLongitude: Double? = null
    private var lastRecordingLatitude: Double? = null
    private var lastRecordingLongitude: Double? = null
    private var lastDistanceToSegmentEndM: Double? = null

    override fun onCreate() {
        super.onCreate()
        observeRecordingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                if (!trackId.isNullOrBlank()) {
                    startRecording(trackId)
                }
            }

            ACTION_START_FOREGROUND -> {
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
                startForegroundOnly(trackId)
            }

            ACTION_STOP -> {
                if (recordingManager.recordingState.value is SensorRecordingState.Recording) {
                    stopRecording()
                } else {
                    disableAutoMonitoringAndStopService()
                }
            }

            ACTION_STOP_FOREGROUND -> {
                stopForegroundOnly()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        previewManager.stopPreview(AUTO_PREVIEW_CLIENT_ID)
        releaseWakeLock()
        super.onDestroy()
        serviceScope.cancel()
    }

    fun startRecording(trackId: String) {
        currentTrackId = trackId
        autoEnabledTrackId = trackId
        startTimeMs = System.currentTimeMillis()
        activeAutoSegment = null
        isAutoStopInProgress = false
        resetStartCandidate()

        ensureForegroundNotification(msgRecording())
        previewManager.pauseAll()

        serviceScope.launch {
            recordingManager.startRecording(trackId, "POCKET_THIGH")
                .onSuccess {
                    _recordingState.value = RecordingState.Recording(
                        trackId = trackId,
                        startTimeMs = startTimeMs
                    )
                }
                .onFailure {
                    Log.w(TAG, "Manual recording start failed: ${it.message}")
                    previewManager.resumeAll()
                    startAutoPreviewIfPossible()
                    _recordingState.value = RecordingState.Error(
                        it.message ?: msgFailedStartRecording()
                    )
                    ensureForegroundNotification(msgAutoArmed())
                }
        }
    }

    fun startForegroundOnly(trackId: String?) {
        val sanitizedTrackId = trackId?.takeIf { it.isNotBlank() }
        if (sanitizedTrackId != null) {
            currentTrackId = sanitizedTrackId
            autoEnabledTrackId = sanitizedTrackId
            loadLocalSegments(sanitizedTrackId)
        }

        ensureForegroundNotification(msgAutoArmed())
        startAutoPreviewIfPossible()
        _recordingState.value = RecordingState.Idle
    }

    fun stopRecording() {
        serviceScope.launch {
            _recordingState.value = RecordingState.Stopping

            val handle = recordingManager.stopRecording()

            if (handle != null) {
                val runResult = processRunUseCase(handle)
                recordingManager.cancelRecording()
                _recordingState.value = runResult.fold(
                    onSuccess = { RecordingState.Completed(handle) },
                    onFailure = {
                        RecordingState.Error(it.message ?: msgFailedProcessRun())
                    }
                )
            } else {
                Log.w(TAG, "Recording stop requested but no capture handle was returned.")
                _recordingState.value = RecordingState.Error(msgFailedStopRecording())
            }

            onRecordingFinished()
        }
    }

    fun stopForegroundOnly() {
        if (autoEnabledTrackId != null) {
            ensureForegroundNotification(msgAutoArmed())
            _recordingState.value = RecordingState.Idle
            return
        }
        stopForegroundAndSelf()
    }

    private fun disableAutoMonitoringAndStopService() {
        autoEnabledTrackId = null
        autoSegments = emptyList()
        activeAutoSegment = null
        isAutoStopInProgress = false
        resetStartCandidate()
        previewManager.stopPreview(AUTO_PREVIEW_CLIENT_ID)
        stopForegroundAndSelf()
    }

    private fun onRecordingFinished() {
        isAutoStopInProgress = false
        activeAutoSegment = null
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        resetStartCandidate()

        previewManager.resumeAll()
        startAutoPreviewIfPossible()

        if (autoEnabledTrackId != null) {
            ensureForegroundNotification(msgAutoArmed())
        } else {
            stopForegroundAndSelf()
        }
    }

    private fun startAutoPreviewIfPossible() {
        if (autoEnabledTrackId.isNullOrBlank()) return
        if (recordingManager.recordingState.value is SensorRecordingState.Recording) return
        if (recordingManager.recordingState.value is SensorRecordingState.Processing) return

        val started = previewManager.startPreview(
            clientId = AUTO_PREVIEW_CLIENT_ID,
            onMetrics = { },
            onLocation = { location ->
                maybeAutoStartAtSegmentStart(location)
            }
        )

        if (!started) {
            Log.w(TAG, "Auto preview could not start. Waiting for sensors/location availability.")
            ensureForegroundNotification(msgWaitingSensors())
        }
    }

    private fun loadLocalSegments(trackId: String) {
        serviceScope.launch {
            getTrackSegmentsUseCase(trackId)
                .onSuccess { segments ->
                    autoSegments = segments.sortedByDescending { it.startedAt }
                }
                .onFailure {
                    autoSegments = emptyList()
                }
        }
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            recordingManager.recordingState.collect { state ->
                when (state) {
                    is SensorRecordingState.Idle -> {
                        if (autoEnabledTrackId != null && !isAutoStopInProgress) {
                            ensureForegroundNotification(msgAutoArmed())
                        }
                    }

                    is SensorRecordingState.Completed -> {
                        if (autoEnabledTrackId != null) {
                            ensureForegroundNotification(msgAutoArmed())
                        }
                    }

                    is SensorRecordingState.Error -> {
                        updateNotification(
                            createNotification(
                                status = state.message.ifBlank { msgFailedStopRecording() }
                            )
                        )
                    }

                    is SensorRecordingState.Processing -> {
                        updateNotification(createNotification(msgProcessing()))
                    }

                    is SensorRecordingState.Recording -> {
                        val elapsed = formatElapsed(System.currentTimeMillis() - startTimeMs)
                        val speed = String.format("%.1f km/h", state.currentSpeed * 3.6f)
                        updateNotification(
                            createNotification(
                                status = msgRecording(),
                                elapsed = elapsed,
                                speed = speed
                            )
                        )
                        maybeAutoStopAtSegmentEnd(state)
                    }
                }
            }
        }
    }

    private fun maybeAutoStartAtSegmentStart(location: PreviewLocation) {
        if (recordingManager.recordingState.value is SensorRecordingState.Recording) {
            updateLastPreviewLocation(location)
            return
        }
        if (recordingManager.recordingState.value is SensorRecordingState.Processing) {
            updateLastPreviewLocation(location)
            return
        }
        if (autoSegments.isEmpty()) {
            updateLastPreviewLocation(location)
            return
        }

        val gpsSensitivity = sensitivityRepository.currentSettings.gpsSensitivity
        val maxPreviewAccuracy = (MAX_PREVIEW_ACCURACY_M / gpsSensitivity.coerceAtLeast(0.01f))
            .coerceIn(10f, 60f)

        if (location.accuracy <= 0f || location.accuracy > maxPreviewAccuracy) {
            resetStartCandidate()
            updateLastPreviewLocation(location)
            return
        }
        if (location.speed < AUTO_START_MIN_SPEED_MPS) {
            resetStartCandidate()
            updateLastPreviewLocation(location)
            return
        }

        val now = System.currentTimeMillis()
        if (now < autoCooldownUntilMs) {
            updateLastPreviewLocation(location)
            return
        }

        var closestSegment: TrackSegment? = null
        var closestDistance = Double.MAX_VALUE

        for (segment in autoSegments) {
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
            updateLastPreviewLocation(location)
            return
        }

        if (startCandidateSegment?.id != closestSegment.id) {
            startCandidateSegment = closestSegment
            startCandidateHits = 0
        }

        if (closestDistance > SEGMENT_START_RELEASE_RADIUS_M) {
            startCandidateHits = 0
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
        } else {
            startCandidateHits = max(0, startCandidateHits - 1)
        }

        if (startCandidateHits >= MIN_START_CONFIRMATION_HITS) {
            autoCooldownUntilMs = now + AUTO_TRIGGER_COOLDOWN_MS
            startAutoSegmentRecording(closestSegment)
            resetStartCandidate()
        }

        updateLastPreviewLocation(location)
    }

    private fun startAutoSegmentRecording(segment: TrackSegment) {
        val trackId = autoEnabledTrackId ?: currentTrackId ?: return
        if (recordingManager.recordingState.value is SensorRecordingState.Recording) return
        if (recordingManager.recordingState.value is SensorRecordingState.Processing) return

        activeAutoSegment = segment
        isAutoStopInProgress = false
        lastDistanceToSegmentEndM = null
        lastRecordingLatitude = null
        lastRecordingLongitude = null
        startTimeMs = System.currentTimeMillis()

        ensureForegroundNotification(msgRecording())
        previewManager.pauseAll()

        serviceScope.launch {
            recordingManager.startRecording(trackId, "POCKET_THIGH")
                .onFailure {
                    Log.w(TAG, "Auto recording start failed for segment ${segment.id}: ${it.message}")
                    activeAutoSegment = null
                    isAutoStopInProgress = false
                    previewManager.resumeAll()
                    startAutoPreviewIfPossible()
                    ensureForegroundNotification(msgAutoArmed())
                }
        }
    }

    private fun maybeAutoStopAtSegmentEnd(state: SensorRecordingState.Recording) {
        if (isAutoStopInProgress) return
        val segment = activeAutoSegment ?: return
        val latitude = state.currentLatitude ?: return
        val longitude = state.currentLongitude ?: return

        val elapsedMs = System.currentTimeMillis() - startTimeMs
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
            stopRecording()
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

    private fun createNotification(
        status: String,
        elapsed: String = "",
        distance: String = "",
        speed: String = ""
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            if (elapsed.isNotEmpty()) {
                append(tr(this@RecordingService, "Time: $elapsed", "Tiempo: $elapsed"))
            }
            if (distance.isNotEmpty()) append(" | $distance")
            if (speed.isNotEmpty()) append(" | $speed")
        }.ifEmpty { status }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("dropIn DH - $status")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_stop,
                tr(this, "Stop", "Detener"),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureForegroundNotification(status: String) {
        acquireWakeLock()
        if (!isForegroundActive) {
            startForeground(NOTIFICATION_ID, createNotification(status))
            isForegroundActive = true
        } else {
            updateNotification(createNotification(status))
        }
    }

    private fun stopForegroundAndSelf() {
        if (isForegroundActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundActive = false
        }
        releaseWakeLock()
        _recordingState.value = RecordingState.Idle
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "dropInDH:RecordingWakeLock"
        ).apply {
            setReferenceCounted(false)
            try {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to acquire wakelock", t)
            }
        }
    }

    private fun releaseWakeLock() {
        val held = wakeLock
        if (held?.isHeld == true) {
            try {
                held.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to release wakelock cleanly", t)
            }
        }
        wakeLock = null
    }

    private fun updateNotification(notification: Notification) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatElapsed(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun msgAutoArmed(): String =
        tr(this, "Auto-start armed", "Auto-inicio armado")

    private fun msgWaitingSensors(): String =
        tr(this, "Waiting for sensor preview", "Esperando vista previa de sensores")

    private fun msgRecording(): String =
        tr(this, "Recording...", "Grabando...")

    private fun msgProcessing(): String =
        tr(this, "Processing run data...", "Procesando datos de la bajada...")

    private fun msgFailedStartRecording(): String =
        tr(this, "Failed to start recording", "No se pudo iniciar la grabación")

    private fun msgFailedStopRecording(): String =
        tr(this, "Failed to stop recording", "No se pudo detener la grabación")

    private fun msgFailedProcessRun(): String =
        tr(this, "Failed to process run", "No se pudo procesar la bajada")

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }
}

sealed class RecordingState {
    data object Idle : RecordingState()
    data class Recording(val trackId: String, val startTimeMs: Long) : RecordingState()
    data object Stopping : RecordingState()
    data class Completed(val handle: com.dhmeter.domain.model.RawCaptureHandle) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

