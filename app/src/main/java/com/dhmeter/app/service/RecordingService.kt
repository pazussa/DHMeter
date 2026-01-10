package com.dhmeter.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dhmeter.app.ui.MainActivity
import com.dhmeter.app.R
import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.monitor.LiveMonitor
import com.dhmeter.sensing.monitor.LiveMetrics
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
 * Foreground service for recording sensor data during a run.
 * Keeps the app running and sensors active even when in background.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "dhmeter_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.dhmeter.action.START_RECORDING"
        const val ACTION_STOP = "com.dhmeter.action.STOP_RECORDING"
        const val EXTRA_TRACK_ID = "track_id"
    }

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var liveMonitor: LiveMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = RecordingBinder()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var currentTrackId: Long = -1
    private var startTimeMs: Long = 0

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1)
                if (trackId != -1L) {
                    startRecording(trackId)
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            recordingManager.stopRecording()
        }
        serviceScope.cancel()
    }

    fun startRecording(trackId: Long) {
        currentTrackId = trackId
        startTimeMs = System.currentTimeMillis()

        // Start foreground notification
        startForeground(NOTIFICATION_ID, createNotification("Recording..."))

        serviceScope.launch {
            recordingManager.startRecording(trackId.toString(), "POCKET_THIGH")
            _recordingState.value = RecordingState.Recording(
                trackId = trackId,
                startTimeMs = startTimeMs
            )
        }
    }

    fun stopRecording() {
        serviceScope.launch {
            _recordingState.value = RecordingState.Stopping

            val handle = recordingManager.stopRecording()
            
            _recordingState.value = if (handle != null) {
                RecordingState.Completed(handle)
            } else {
                RecordingState.Error("Failed to stop recording")
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
            if (elapsed.isNotEmpty()) append("Time: $elapsed")
            if (distance.isNotEmpty()) append(" | $distance")
            if (speed.isNotEmpty()) append(" | $speed")
        }.ifEmpty { status }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DH Meter - $status")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_recording)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }
}

sealed class RecordingState {
    data object Idle : RecordingState()
    data class Recording(val trackId: Long, val startTimeMs: Long) : RecordingState()
    data object Stopping : RecordingState()
    data class Completed(val handle: com.dhmeter.domain.model.RawCaptureHandle) : RecordingState()
    data class Error(val message: String) : RecordingState()
}
