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
import com.dhmeter.domain.usecase.ProcessRunUseCase
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingState as SensorRecordingState
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

/**
 * Foreground service for active recording and manual recording standby.
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
        const val EXTRA_AUTO_NAVIGATE_TRACK_ID = "auto_navigate_track_id"
        const val EXTRA_AUTO_NAVIGATE_RUN_ID = "auto_navigate_run_id"

        private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L
    }

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var processRunUseCase: ProcessRunUseCase

    @Inject
    lateinit var previewManager: RecordingPreviewManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = RecordingBinder()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var currentTrackId: String? = null

    private var startTimeMs: Long = 0
    private var isForegroundActive: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null

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
                    stopServiceSafely()
                }
            }

            ACTION_STOP_FOREGROUND -> {
                stopForegroundOnly()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop standby/recording service when the user explicitly closes the app task.
        stopServiceSafely(cancelActiveRecording = true)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
        serviceScope.cancel()
    }

    fun startRecording(trackId: String) {
        currentTrackId = trackId
        startTimeMs = System.currentTimeMillis()

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
                    _recordingState.value = RecordingState.Error(
                        it.message ?: msgFailedStartRecording()
                    )
                    ensureForegroundNotification(msgReadyMonitoring())
                }
        }
    }

    fun startForegroundOnly(trackId: String?) {
        val sanitizedTrackId = trackId?.takeIf { it.isNotBlank() }
        if (sanitizedTrackId != null) {
            currentTrackId = sanitizedTrackId
        }

        ensureForegroundNotification(msgReadyMonitoring())
        _recordingState.value = RecordingState.Idle
    }

    fun stopRecording(navigateToRunSummary: Boolean = false) {
        serviceScope.launch {
            _recordingState.value = RecordingState.Stopping

            val handle = recordingManager.stopRecording()

            if (handle != null) {
                val runResult = processRunUseCase(handle)
                runResult.onSuccess { runId ->
                    if (navigateToRunSummary) {
                        redirectAppToRunSummary(runId)
                    }
                }
                recordingManager.cancelRecording()
                _recordingState.value = runResult.fold(
                    onSuccess = { runId -> RecordingState.Completed(handle, runId) },
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
        when (recordingManager.recordingState.value) {
            is SensorRecordingState.Recording -> {
                ensureForegroundNotification(msgRecording())
                _recordingState.value = RecordingState.Idle
            }
            is SensorRecordingState.Processing -> {
                ensureForegroundNotification(msgProcessing())
                _recordingState.value = RecordingState.Idle
            }
            else -> {
                stopServiceSafely()
            }
        }
    }

    private fun stopServiceSafely(cancelActiveRecording: Boolean = false) {
        if (cancelActiveRecording || recordingManager.recordingState.value is SensorRecordingState.Processing) {
            recordingManager.cancelRecording()
        }
        previewManager.resumeAll()
        stopForegroundAndSelf()
    }

    private fun onRecordingFinished() {
        previewManager.resumeAll()
        stopForegroundAndSelf()
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            recordingManager.recordingState.collect { state ->
                when (state) {
                    is SensorRecordingState.Idle -> {
                        ensureForegroundNotification(msgReadyMonitoring())
                    }

                    is SensorRecordingState.Completed -> {
                        ensureForegroundNotification(msgReadyMonitoring())
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
                    }
                }
            }
        }
    }

    private fun redirectAppToRunSummary(runId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_NAVIGATE_RUN_ID, runId)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Failed to redirect UI to run summary", it) }
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

    private fun msgReadyMonitoring(): String =
        tr(this, "Recording standby", "Grabación en espera")

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
    data class Completed(
        val handle: com.dhmeter.domain.model.RawCaptureHandle,
        val runId: String? = null
    ) : RecordingState()
    data class Error(val message: String) : RecordingState()
}
