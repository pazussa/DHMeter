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
        clientId: String,
        onMetrics: (LiveMetrics) -> Unit,
        onLocation: ((PreviewLocation) -> Unit)? = null
    ): Boolean
    fun stopPreview(clientId: String)
    fun pauseAll()
    fun resumeAll()
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
    private val clients = LinkedHashMap<String, PreviewClient>()
    private val lock = Any()
    private var previewJob: Job? = null
    private var previewBuffers: SensorBuffers? = null
    private var isPaused: Boolean = false

    override fun startPreview(
        clientId: String,
        onMetrics: (LiveMetrics) -> Unit,
        onLocation: ((PreviewLocation) -> Unit)?
    ): Boolean {
        synchronized(lock) {
            clients[clientId] = PreviewClient(onMetrics, onLocation)
            if (isPaused) return true
        }

        if (previewJob?.isActive == true) return true
        return startCollectorsAndMonitoring()
    }

    override fun stopPreview(clientId: String) {
        val shouldStop = synchronized(lock) {
            clients.remove(clientId)
            clients.isEmpty()
        }
        if (shouldStop) {
            stopCollectorsAndMonitoring()
        }
    }

    override fun pauseAll() {
        synchronized(lock) {
            isPaused = true
        }
        stopCollectorsAndMonitoring()
    }

    override fun resumeAll() {
        val shouldStart = synchronized(lock) {
            val canStart = isPaused && clients.isNotEmpty()
            if (canStart) {
                isPaused = false
            }
            canStart
        }
        if (shouldStart && previewJob?.isActive != true) {
            startCollectorsAndMonitoring()
        }
    }

    private fun startCollectorsAndMonitoring(): Boolean {
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
                val latestGps = buffers.gps.getAll().lastOrNull()
                val snapshot = synchronized(lock) { clients.values.toList() }
                snapshot.forEach { client ->
                    client.onMetrics(metrics)
                    if (client.onLocation != null && latestGps != null) {
                        client.onLocation.invoke(
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

    private fun stopCollectorsAndMonitoring() {
        previewJob?.cancel()
        previewJob = null
        liveMonitor.stopMonitoring()
        gpsCollector.stop()
        imuCollector.stop()
        previewBuffers?.clear()
        previewBuffers = null
    }

    private data class PreviewClient(
        val onMetrics: (LiveMetrics) -> Unit,
        val onLocation: ((PreviewLocation) -> Unit)?
    )
}
