package com.dropindh.app.simulation

import com.dropindh.app.ui.metrics.normalizeSeriesBurdenScore
import com.dhmeter.domain.model.SensorSensitivitySettings
import com.dhmeter.domain.model.SeriesType
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.sensing.data.GpsSample
import com.dhmeter.sensing.data.GyroSample
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.sensing.monitor.LiveMetrics
import com.dhmeter.sensing.monitor.LiveMonitor
import com.dhmeter.signal.metrics.HarshnessAnalyzer
import com.dhmeter.signal.metrics.ImpactAnalyzer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LiveVsChartsSimulationTest {

    @Test
    fun liveMonitor_and_chartSeries_normalization_stay_aligned_on_simulated_data() = runBlocking {
        val sensitivityRepository = FakeSensorSensitivityRepository()
        val liveMonitor = LiveMonitor(sensitivityRepository)
        val impactAnalyzer = ImpactAnalyzer(sensitivityRepository)
        val harshnessAnalyzer = HarshnessAnalyzer(sensitivityRepository)

        val buffers = SensorBuffers(
            accelCapacity = 10_000,
            gyroCapacity = 10_000,
            gpsCapacity = 1_000
        )
        SimulatedRunFactory.fillBuffers(
            buffers = buffers,
            impactAmplitudeMs2 = 4.5f,
            vibrationAmplitudeMs2 = 1.5f
        )

        val liveMetrics = sampleOnce(liveMonitor, buffers)
        val accelWindow = buffers.accel.getAll().takeLast(200)

        val impactRaw = impactAnalyzer.analyzeWindow(accelWindow)
        val harshnessRaw = harshnessAnalyzer.analyzeWindow(accelWindow, 200f)

        val chartImpact01 = normalizeSeriesBurdenScore(SeriesType.IMPACT_DENSITY, impactRaw) / 100f
        val chartHarsh01 = normalizeSeriesBurdenScore(SeriesType.HARSHNESS, harshnessRaw) / 100f

        assertTrue("expected moderate impacts to be visible in chart", chartImpact01 > 0.01f)
        assertTrue("expected moderate impacts to be visible in live monitor", liveMetrics.liveImpact > 0.01f)
        assertEquals("impact live vs chart mismatch", chartImpact01, liveMetrics.liveImpact, 0.08f)
        assertEquals("harshness live vs chart mismatch", chartHarsh01, liveMetrics.liveHarshness, 0.08f)
    }

    private suspend fun sampleOnce(liveMonitor: LiveMonitor, buffers: SensorBuffers): LiveMetrics {
        val firstMetric = CompletableDeferred<LiveMetrics>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            liveMonitor.startMonitoring(buffers) { metrics ->
                if (!firstMetric.isCompleted) {
                    firstMetric.complete(metrics)
                    liveMonitor.stopMonitoring()
                }
            }
        }

        return try {
            withTimeout(2_000) {
                firstMetric.await()
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for live monitor sample", e)
        } finally {
            liveMonitor.stopMonitoring()
            job.cancel()
        }
    }
}

private class FakeSensorSensitivityRepository : SensorSensitivityRepository {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(SensorSensitivitySettings())
    override val settings = mutable
    override val currentSettings: SensorSensitivitySettings
        get() = mutable.value

    override suspend fun updateImpactSensitivity(value: Float) {
        mutable.value = mutable.value.copy(impactSensitivity = value).normalized()
    }

    override suspend fun updateHarshnessSensitivity(value: Float) {
        mutable.value = mutable.value.copy(harshnessSensitivity = value).normalized()
    }

    override suspend fun updateStabilitySensitivity(value: Float) {
        mutable.value = mutable.value.copy(stabilitySensitivity = value).normalized()
    }

    override suspend fun updateGpsSensitivity(value: Float) {
        mutable.value = mutable.value.copy(gpsSensitivity = value).normalized()
    }

    override suspend fun resetToDefaults() {
        mutable.value = SensorSensitivitySettings()
    }
}

private object SimulatedRunFactory {
    fun fillBuffers(
        buffers: SensorBuffers,
        impactAmplitudeMs2: Float = 4.5f,
        vibrationAmplitudeMs2: Float = 1.5f
    ) {
        val sampleRateHz = 200
        val totalSeconds = 8
        val totalSamples = sampleRateHz * totalSeconds
        val dtNs = 1_000_000_000L / sampleRateHz

        var timestampNs = 0L
        val gpsDtNs = 1_000_000_000L
        var nextGpsNs = 0L

        val startLat = 40.0
        val startLon = -3.0
        var lat = startLat
        var lon = startLon
        val speedMps = 7.0f

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRateHz

            val vibration = vibrationAmplitudeMs2 * sin(2.0 * PI * 25.0 * t).toFloat()
            val impactPulse = impactPulseAt(t, impactAmplitudeMs2)
            val ax = 0.25f * sin(2.0 * PI * 3.0 * t).toFloat()
            val ay = 0.2f * sin(2.0 * PI * 2.0 * t).toFloat()
            val az = vibration + impactPulse

            buffers.accel.add(
                AccelSample(
                    timestampNs = timestampNs,
                    x = ax,
                    y = ay,
                    z = az
                )
            )

            val gx = 0.08f * sin(2.0 * PI * 1.2 * t).toFloat()
            val gy = 0.10f * sin(2.0 * PI * 0.9 * t + 0.3).toFloat()
            val gz = 0.05f * sin(2.0 * PI * 0.7 * t).toFloat()
            buffers.gyro.add(
                GyroSample(
                    timestampNs = timestampNs,
                    x = gx,
                    y = gy,
                    z = gz
                )
            )

            if (timestampNs >= nextGpsNs) {
                val metersPerDegLat = 111_132.0
                val deltaLat = (speedMps / metersPerDegLat).toDouble()
                lat += deltaLat
                lon += 0.0
                buffers.gps.add(
                    GpsSample(
                        timestampNs = timestampNs,
                        latitude = lat,
                        longitude = lon,
                        altitude = null,
                        speed = speedMps,
                        accuracy = 5.5f
                    )
                )
                nextGpsNs += gpsDtNs
            }

            timestampNs += dtNs
        }
    }

    private fun impactPulseAt(t: Double, amplitudeMs2: Float): Float {
        val pulseCenters = listOf(1.2, 2.8, 4.4, 6.0, 7.4)
        val sigma = 0.018
        var value = 0.0
        for (center in pulseCenters) {
            val dist = t - center
            value += amplitudeMs2 * kotlin.math.exp(-(dist * dist) / (2.0 * sigma * sigma))
        }
        return value.toFloat()
    }
}

