package com.dhmeter.sensing

import android.os.Build
import com.dhmeter.domain.model.GpsQuality
import com.dhmeter.domain.model.RawCaptureHandle
import com.dhmeter.sensing.collector.GpsCollector
import com.dhmeter.sensing.collector.ImuCollector
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.sensing.monitor.LiveMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingManagerImpl @Inject constructor(
    private val imuCollector: ImuCollector,
    private val gpsCollector: GpsCollector,
    private val liveMonitor: LiveMonitor,
    private val sensorBuffers: SensorBuffers
) : RecordingManager {

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var sessionId: String? = null
    private var trackId: String? = null
    private var startTimeNs: Long = 0
    private var monitorJob: Job? = null

    override suspend fun startRecording(trackId: String, placement: String): Result<Unit> {
        if (_recordingState.value is RecordingState.Recording) {
            return Result.failure(IllegalStateException("Already recording"))
        }

        this.trackId = trackId
        sessionId = UUID.randomUUID().toString()
        startTimeNs = System.nanoTime()

        // Clear buffers
        sensorBuffers.clear()

        // Start collectors
        if (!imuCollector.start(sensorBuffers)) {
            return Result.failure(Exception("Failed to start IMU sensors"))
        }

        if (!gpsCollector.start(sensorBuffers)) {
            imuCollector.stop()
            return Result.failure(Exception("Failed to start GPS"))
        }

        // Start live monitoring
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            liveMonitor.startMonitoring(sensorBuffers) { metrics ->
                _recordingState.value = RecordingState.Recording(
                    gpsAccuracy = metrics.gpsAccuracy,
                    movementDetected = metrics.movementDetected,
                    signalStability = metrics.signalStability,
                    currentSpeed = metrics.currentSpeed,
                    liveImpact = metrics.liveImpact,
                    liveHarshness = metrics.liveHarshness,
                    liveStability = metrics.liveStability
                )
            }
        }

        _recordingState.value = RecordingState.Recording(
            gpsAccuracy = -1f,
            movementDetected = false,
            signalStability = -1f,
            currentSpeed = 0f,
            liveImpact = 0f,
            liveHarshness = 0f,
            liveStability = 0f
        )

        return Result.success(Unit)
    }

    override suspend fun stopRecording(): RawCaptureHandle? {
        if (_recordingState.value !is RecordingState.Recording) {
            return null
        }

        _recordingState.value = RecordingState.Processing

        monitorJob?.cancel()
        liveMonitor.stopMonitoring()

        val imuStats = imuCollector.stop()
        val gpsStats = gpsCollector.stop()

        val endTimeNs = System.nanoTime()

        val gpsQuality = when {
            gpsStats.avgAccuracy <= 5f -> GpsQuality.GOOD
            gpsStats.avgAccuracy <= 15f -> GpsQuality.MEDIUM
            else -> GpsQuality.POOR
        }

        val handle = RawCaptureHandle(
            sessionId = sessionId ?: UUID.randomUUID().toString(),
            trackId = trackId ?: "",
            startTimeNs = startTimeNs,
            endTimeNs = endTimeNs,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            accelSampleRate = imuStats.accelSampleRate,
            gyroSampleRate = imuStats.gyroSampleRate,
            baroSampleRate = null, // No barometer in MVP2
            hasBarometer = false,
            gpsPointCount = gpsStats.sampleCount,
            accelSampleCount = imuStats.accelSampleCount,
            gyroSampleCount = imuStats.gyroSampleCount,
            baroSampleCount = 0
        )

        // Keep buffers available for processing
        // They will be cleared on next startRecording

        return handle
    }

    override fun cancelRecording() {
        monitorJob?.cancel()
        liveMonitor.stopMonitoring()
        imuCollector.stop()
        gpsCollector.stop()
        sensorBuffers.clear()
        _recordingState.value = RecordingState.Idle
    }
}
