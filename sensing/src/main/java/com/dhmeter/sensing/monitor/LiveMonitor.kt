package com.dhmeter.sensing.monitor

import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.sensing.data.SensorBuffers
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Monitors sensor data in real-time to provide live feedback.
 * MVP2: Uses GPS speed for movement detection (no barometer).
 * Calculates live Impact, Harshness, and Stability metrics.
 */
@Singleton
class LiveMonitor @Inject constructor(
    private val sensitivityRepository: SensorSensitivityRepository
) {

    companion object {
        const val MIN_MOVING_SPEED_MPS = 2.22f // 8 km/h
        const val GRAVITY = 9.81f
        const val IMPACT_THRESHOLD_MS2 = 2.5f // LINEAR_ACCEL threshold at sensitivity=1.0

        // Harshness bandpass filter coefficients (simplified for real-time)
        const val HARSHNESS_LOW_CUTOFF = 15f
        const val HARSHNESS_HIGH_CUTOFF = 40f

        // Sample rate estimation
        const val ESTIMATED_SAMPLE_RATE = 200f
    }

    private var isMonitoring = false

    suspend fun startMonitoring(
        buffers: SensorBuffers,
        onMetrics: (LiveMetrics) -> Unit
    ) {
        isMonitoring = true

        while (isMonitoring) {
            val metrics = calculateMetrics(buffers)
            onMetrics(metrics)
            delay(300) // Update every 300ms for smoother live display
        }
    }

    fun stopMonitoring() {
        isMonitoring = false
    }

    private fun calculateMetrics(buffers: SensorBuffers): LiveMetrics {
        // Get recent GPS accuracy
        val recentGps = buffers.gps.getAll().takeLast(5)
        val latestGps = recentGps.lastOrNull()
        val gpsAccuracy = if (recentGps.isNotEmpty()) {
            recentGps.map { it.accuracy }.average().toFloat()
        } else -1f

        // Get current speed
        val currentSpeed = latestGps?.speed ?: 0f

        // Check for movement (using GPS speed)
        val movementDetected = checkMovementDetected(buffers)

        // Check signal stability (phone not loose in pocket)
        val signalStability = calculateSignalStability(buffers)

        // Calculate live metrics from recent sensor data
        val liveImpact = calculateLiveImpact(buffers)
        val liveHarshness = calculateLiveHarshness(buffers)
        val liveStability = calculateLiveStabilityIndex(buffers)

        return LiveMetrics(
            gpsAccuracy = gpsAccuracy,
            movementDetected = movementDetected,
            signalStability = signalStability,
            currentSpeed = currentSpeed,
            latitude = latestGps?.latitude,
            longitude = latestGps?.longitude,
            liveImpact = liveImpact,
            liveHarshness = liveHarshness,
            liveStability = liveStability
        )
    }

    /**
     * Calculate live impact score from recent accelerometer data.
     * Uses peak detection above threshold.
     */
    private fun calculateLiveImpact(buffers: SensorBuffers): Float {
        val accelSamples = buffers.accel.getAll().takeLast(200) // ~1s at 200Hz
        if (accelSamples.size < 50) return 0f

        val magnitudes = accelSamples.map { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }.toFloatArray()
        val impactSensitivity = sensitivityRepository.currentSettings.impactSensitivity
        val thresholdMs2 = IMPACT_THRESHOLD_MS2 / impactSensitivity.coerceAtLeast(0.01f)
        val sampleRateHz = estimateSampleRate(accelSamples)
        val minDistanceSamples = ((100f / 1000f) * sampleRateHz).toInt().coerceAtLeast(1)

        val peaks = detectImpactPeaks(
            signal = magnitudes,
            threshold = thresholdMs2,
            minDistanceSamples = minDistanceSamples
        )
        val impactScore = peaks.sumOf { idx ->
            val peakG = magnitudes[idx] / GRAVITY
            peakG.toDouble().pow(2)
        }.toFloat()

        // Normalize to 0-1 range (max ~5 means very rough)
        return (impactScore / 5f).coerceIn(0f, 1f)
    }

    private fun detectImpactPeaks(
        signal: FloatArray,
        threshold: Float,
        minDistanceSamples: Int
    ): List<Int> {
        if (signal.size < 3) return emptyList()

        val peaks = mutableListOf<Int>()

        for (i in 1 until signal.lastIndex) {
            val current = signal[i]
            if (current <= threshold) continue

            val prev = signal[i - 1]
            val next = signal[i + 1]
            val isLocalMax = current >= prev && current >= next && (current > prev || current > next)
            if (!isLocalMax) continue

            val lastPeakIdx = peaks.lastOrNull()
            if (lastPeakIdx != null && (i - lastPeakIdx) < minDistanceSamples) {
                if (current > signal[lastPeakIdx]) {
                    peaks[peaks.lastIndex] = i
                }
            } else {
                peaks.add(i)
            }
        }

        return peaks
    }

    private fun estimateSampleRate(samples: List<com.dhmeter.sensing.data.AccelSample>): Float {
        if (samples.size < 2) return ESTIMATED_SAMPLE_RATE

        val durationNs = (samples.last().timestampNs - samples.first().timestampNs).coerceAtLeast(1L)
        val intervals = (samples.size - 1).coerceAtLeast(1)
        val sampleRate = intervals * 1_000_000_000f / durationNs.toFloat()
        return if (sampleRate.isFinite()) {
            sampleRate.coerceIn(20f, 500f)
        } else {
            ESTIMATED_SAMPLE_RATE
        }
    }

    /**
     * Calculate live harshness (vibration RMS) from recent accelerometer data.
     * Simplified high-frequency energy calculation.
     */
    private fun calculateLiveHarshness(buffers: SensorBuffers): Float {
        val accelSamples = buffers.accel.getAll().takeLast(200) // ~1s at 200Hz
        if (accelSamples.size < 50) return 0f

        // Calculate magnitude
        val magnitudes = accelSamples.map { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }

        // Simple high-pass to remove gravity/low-frequency: difference filter
        val highFreq = magnitudes.zipWithNext { a, b -> (b - a).pow(2) }

        // RMS of high-frequency content
        val rms = sqrt(highFreq.average().toFloat())

        // Normalize to 0-1 range (typical harsh terrain ~2-3 m/s^2)
        val harshnessSensitivity = sensitivityRepository.currentSettings.harshnessSensitivity
        return ((rms * harshnessSensitivity) / 3f).coerceIn(0f, 1f)
    }

    /**
     * Calculate live stability index from gyroscope data.
     * Uses variance of rotation rates (pitch/roll).
     */
    private fun calculateLiveStabilityIndex(buffers: SensorBuffers): Float {
        val gyroSamples = buffers.gyro.getAll().takeLast(200) // ~1s at 200Hz
        if (gyroSamples.size < 50) return 0f

        // Calculate variance of X and Y rotation (pitch/roll - most indicative of instability)
        val omegaX = gyroSamples.map { it.x }
        val omegaY = gyroSamples.map { it.y }

        val meanX = omegaX.average()
        val meanY = omegaY.average()

        val varX = omegaX.map { (it - meanX).pow(2) }.average().toFloat()
        val varY = omegaY.map { (it - meanY).pow(2) }.average().toFloat()

        val stabilitySensitivity = sensitivityRepository.currentSettings.stabilitySensitivity
        val stabilityIndex = (varX + varY) * stabilitySensitivity

        // Normalize to 0-1 range (higher = less stable)
        // Typical unstable: >0.5 rad^2/s^2, very stable: <0.05 rad^2/s^2
        return (stabilityIndex / 0.5f).coerceIn(0f, 1f)
    }

    /**
     * Check if user is actively moving based on GPS speed.
     * Replaces barometer descent detection.
     */
    private fun checkMovementDetected(buffers: SensorBuffers): Boolean {
        val gpsSamples = buffers.gps.getAll()
        if (gpsSamples.size < 3) return false

        // Check average speed over last few samples
        val recent = gpsSamples.takeLast(5)
        val avgSpeed = recent.map { it.speed }.average()

        return avgSpeed >= MIN_MOVING_SPEED_MPS
    }

    private fun calculateSignalStability(buffers: SensorBuffers): Float {
        val accelSamples = buffers.accel.getAll().takeLast(200)
        if (accelSamples.size < 100) return -1f

        // Gyro variance is also important for stability
        val gyroSamples = buffers.gyro.getAll().takeLast(200)
        if (gyroSamples.size < 100) return -1f

        val gyroMags = gyroSamples.map {
            sqrt(it.x * it.x + it.y * it.y + it.z * it.z)
        }
        val gyroMean = gyroMags.average()
        val gyroVariance = gyroMags.map { (it - gyroMean) * (it - gyroMean) }.average()
        val stabilitySensitivity = sensitivityRepository.currentSettings.stabilitySensitivity
        val adjustedGyroVariance = gyroVariance * stabilitySensitivity

        // Simple stability score (1 = stable, 0 = unstable)
        val stabilityScore = when {
            adjustedGyroVariance > 10 -> 0.2f // Very unstable
            adjustedGyroVariance > 5 -> 0.5f  // Moderate
            adjustedGyroVariance > 2 -> 0.7f  // Good
            else -> 0.9f // Very stable
        }

        return stabilityScore
    }
}

/**
 * Live metrics data class with all real-time measurements.
 */
data class LiveMetrics(
    val gpsAccuracy: Float,
    val movementDetected: Boolean,
    val signalStability: Float,
    val currentSpeed: Float,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val liveImpact: Float = 0f, // 0-1, higher = more impacts
    val liveHarshness: Float = 0f, // 0-1, higher = more vibration
    val liveStability: Float = 0f // 0-1, higher = less stable
)
