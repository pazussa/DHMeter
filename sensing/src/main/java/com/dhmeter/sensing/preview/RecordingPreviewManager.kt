package com.dhmeter.sensing.preview

import com.dhmeter.sensing.collector.GpsCollector
import com.dhmeter.sensing.collector.ImuCollector
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.sensing.monitor.LiveMetrics
import com.dhmeter.sensing.monitor.LiveMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface RecordingPreviewManager {
    fun startPreview(
        onMetrics: (LiveMetrics) -> Unit,
        onLocation: ((PreviewLocation) -> Unit)? = null
    ): Boolean
    fun stopPreview()
}

data class PreviewLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float
)

@Singleton
class RecordingPreviewManagerImpl @Inject constructor(
    private val imuCollector: ImuCollector,
    private val gpsCollector: GpsCollector,
    private val liveMonitor: LiveMonitor
) : RecordingPreviewManager {
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var previewJob: Job? = null
    private var previewBuffers: SensorBuffers? = null

    override fun startPreview(
        onMetrics: (LiveMetrics) -> Unit,
        onLocation: ((PreviewLocation) -> Unit)?
    ): Boolean {
        if (previewJob?.isActive == true) return true

        val buffers = SensorBuffers()
        val imuStarted = imuCollector.start(buffers)
        if (!imuStarted) return false

        val gpsStarted = gpsCollector.start(buffers)
        if (!gpsStarted) {
            imuCollector.stop()
            return false
        }

        previewBuffers = buffers
        previewJob = previewScope.launch {
            liveMonitor.startMonitoring(buffers) { metrics ->
                onMetrics(metrics)
                if (onLocation != null) {
                    val latestGps = buffers.gps.getAll().lastOrNull()
                    if (latestGps != null) {
                        onLocation(
                            PreviewLocation(
                                latitude = latestGps.latitude,
                                longitude = latestGps.longitude,
                                accuracy = latestGps.accuracy,
                                speed = latestGps.speed
                            )
                        )
                    }
                }
            }
        }
        return true
    }

    override fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        liveMonitor.stopMonitoring()
        gpsCollector.stop()
        imuCollector.stop()
        previewBuffers?.clear()
        previewBuffers = null
    }
}
