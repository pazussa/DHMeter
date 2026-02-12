package com.dhmeter.signal.metrics

import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.domain.repository.SensorSensitivityRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analyzes high-frequency vibration (chatter) in acceleration data.
 * Uses a lightweight high-pass proxy (difference energy) aligned with live monitor.
 */
@Singleton
class HarshnessAnalyzer @Inject constructor(
    private val sensitivityRepository: SensorSensitivityRepository
) {

    /**
     * Analyze a window of accelerometer data and return harshness RMS.
     */
    fun analyzeWindow(samples: List<AccelSample>, sampleRate: Float): Float {
        if (sampleRate <= 0f) return 0f
        if (samples.size < 50) return 0f

        val magnitudes = samples.map { sample ->
            sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        }
        val highFreq = magnitudes.zipWithNext { a, b -> (b - a).pow(2) }
        val baseRms = if (highFreq.isNotEmpty()) {
            sqrt(highFreq.average().toFloat())
        } else {
            0f
        }
        val sensitivity = sensitivityRepository.currentSettings.harshnessSensitivity
        return baseRms * sensitivity
    }
}
