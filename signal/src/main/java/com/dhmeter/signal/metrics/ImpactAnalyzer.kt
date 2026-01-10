package com.dhmeter.signal.metrics

import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.signal.dsp.SignalUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analyzes LINEAR_ACCELERATION data to compute impact metrics.
 * Note: LINEAR_ACCELERATION already has gravity removed, so we use magnitude directly.
 */
@Singleton
class ImpactAnalyzer @Inject constructor() {

    companion object {
        const val GRAVITY = 9.81f
        const val IMPACT_THRESHOLD_G = 0.8f // 0.8g threshold (~7.8 m/s²) - captures normal bike impacts
        const val MIN_PEAK_DISTANCE_MS = 100 // Minimum 100ms between peaks
    }

    /**
     * Analyze a window of LINEAR_ACCELERATION data and return impact density score.
     * Uses magnitude directly since LINEAR_ACCELERATION already has gravity removed.
     */
    fun analyzeWindow(samples: List<AccelSample>): Float {
        if (samples.isEmpty()) return 0f

        // Use magnitude directly - LINEAR_ACCELERATION is already gravity-free
        val magnitudes = samples.map { sample ->
            sqrt(sample.x.pow(2) + sample.y.pow(2) + sample.z.pow(2))
        }

        // Find peaks above threshold
        val thresholdMs2 = IMPACT_THRESHOLD_G * GRAVITY
        val peaks = SignalUtils.findPeaks(
            magnitudes.toFloatArray(),
            thresholdMs2,
            minDistanceSamples = 20 // ~100ms at 200Hz
        )

        // Calculate impact score as sum of squared peak severities
        val impactScore = peaks.sumOf { peakIdx ->
            val peakG = magnitudes[peakIdx] / GRAVITY
            peakG.toDouble().pow(2)
        }.toFloat()

        return impactScore
    }

    /**
     * Detect significant impact peaks for event markers.
     */
    fun detectPeaks(samples: List<AccelSample>, sampleRate: Float): List<ImpactPeak> {
        if (samples.isEmpty()) return emptyList()

        // Use magnitude directly - LINEAR_ACCELERATION is already gravity-free
        val magnitudes = samples.map { sample ->
            sqrt(sample.x.pow(2) + sample.y.pow(2) + sample.z.pow(2))
        }

        val minDistanceSamples = (MIN_PEAK_DISTANCE_MS / 1000f * sampleRate).toInt()
        val thresholdMs2 = IMPACT_THRESHOLD_G * GRAVITY

        val peakIndices = SignalUtils.findPeaks(
            magnitudes.toFloatArray(),
            thresholdMs2,
            minDistanceSamples
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
}

data class ImpactPeak(
    val sampleIndex: Int,
    val timestampNs: Long = 0,
    val peakG: Float,
    val severity: Float
)
