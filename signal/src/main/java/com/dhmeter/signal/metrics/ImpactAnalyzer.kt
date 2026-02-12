package com.dhmeter.signal.metrics

import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.sensing.data.AccelSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analyzes LINEAR_ACCELERATION data to compute impact metrics.
 * Note: LINEAR_ACCELERATION already has gravity removed, so we use magnitude directly.
 */
@Singleton
class ImpactAnalyzer @Inject constructor(
    private val sensitivityRepository: SensorSensitivityRepository
) {

    companion object {
        const val GRAVITY = 9.81f
        const val IMPACT_THRESHOLD_MS2 = 2.5f // LINEAR_ACCEL threshold at sensitivity=1.0
        const val MIN_PEAK_DISTANCE_MS = 100 // Minimum 100ms between peaks
        const val DEFAULT_SAMPLE_RATE_HZ = 200f
    }

    /**
     * Analyze a window of LINEAR_ACCELERATION data and return impact density score.
     * Uses magnitude directly since LINEAR_ACCELERATION already has gravity removed.
     */
    fun analyzeWindow(samples: List<AccelSample>): Float {
        if (samples.size < 3) return 0f

        val magnitudes = samples.map { sample ->
            sqrt(sample.x.pow(2) + sample.y.pow(2) + sample.z.pow(2))
        }.toFloatArray()

        val sampleRateHz = estimateSampleRate(samples)
        val peaks = detectPeakIndices(
            signal = magnitudes,
            threshold = impactThresholdMs2(),
            minDistanceSamples = minPeakDistanceSamples(sampleRateHz)
        )

        return peaks.sumOf { peakIdx ->
            val peakG = magnitudes[peakIdx] / GRAVITY
            peakG.toDouble().pow(2)
        }.toFloat()
    }

    /**
     * Detect significant impact peaks for event markers.
     */
    fun detectPeaks(samples: List<AccelSample>, sampleRate: Float): List<ImpactPeak> {
        if (samples.size < 3) return emptyList()

        val magnitudes = samples.map { sample ->
            sqrt(sample.x.pow(2) + sample.y.pow(2) + sample.z.pow(2))
        }.toFloatArray()

        val peakIndices = detectPeakIndices(
            signal = magnitudes,
            threshold = impactThresholdMs2(),
            minDistanceSamples = minPeakDistanceSamples(sampleRate)
        )

        return peakIndices.map { idx ->
            val peakG = magnitudes[idx] / GRAVITY
            ImpactPeak(
                sampleIndex = idx,
                timestampNs = samples[idx].timestampNs,
                peakG = peakG,
                severity = peakG.pow(2)
            )
        }
    }

    private fun impactThresholdMs2(): Float {
        val sensitivity = sensitivityRepository.currentSettings.impactSensitivity
        return IMPACT_THRESHOLD_MS2 / sensitivity.coerceAtLeast(0.01f)
    }

    private fun minPeakDistanceSamples(sampleRate: Float): Int {
        val safeSampleRate = sampleRate.coerceAtLeast(1f)
        return ((MIN_PEAK_DISTANCE_MS / 1000f) * safeSampleRate).toInt().coerceAtLeast(1)
    }

    private fun estimateSampleRate(samples: List<AccelSample>): Float {
        if (samples.size < 2) return DEFAULT_SAMPLE_RATE_HZ

        val durationNs = (samples.last().timestampNs - samples.first().timestampNs).coerceAtLeast(1L)
        val intervals = (samples.size - 1).coerceAtLeast(1)
        val sampleRate = intervals * 1_000_000_000f / durationNs.toFloat()
        return if (sampleRate.isFinite()) {
            sampleRate.coerceIn(20f, 500f)
        } else {
            DEFAULT_SAMPLE_RATE_HZ
        }
    }

    /**
     * Peak detector resilient to flat tops and small oscillations.
     * When two peaks appear too close, keeps the stronger one.
     */
    private fun detectPeakIndices(
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
}

data class ImpactPeak(
    val sampleIndex: Int,
    val timestampNs: Long = 0,
    val peakG: Float,
    val severity: Float
)
