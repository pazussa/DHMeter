package com.dhmeter.signal.metrics

import com.dhmeter.sensing.data.GyroSample
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.signal.dsp.SignalUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes rider/bike stability using gyroscope data.
 * High variance in rotation rates indicates instability.
 */
@Singleton
class StabilityAnalyzer @Inject constructor(
    private val sensitivityRepository: SensorSensitivityRepository
) {

    companion object {
        // Stability thresholds
        const val STABILITY_EXCELLENT = 0.05f
        const val STABILITY_GOOD = 0.15f
        const val STABILITY_MODERATE = 0.30f
    }

    /**
     * Analyze a window of gyroscope data and return stability index.
     * Lower values = more stable.
     */
    fun analyzeWindow(samples: List<GyroSample>): StabilityResult {
        if (samples.size < 10) return StabilityResult(0f, 0f, 0f, 0f)

        val omegaX = samples.map { it.x }.toFloatArray()
        val omegaY = samples.map { it.y }.toFloatArray()
        val omegaZ = samples.map { it.z }.toFloatArray()

        // Calculate variance for each axis
        val varX = SignalUtils.variance(omegaX)
        val varY = SignalUtils.variance(omegaY)
        val varZ = SignalUtils.variance(omegaZ)

        // Combined stability index (X and Y are most relevant for rider motion)
        // Z-axis (vertical rotation/yaw) is less indicative of instability
        val sensitivity = sensitivityRepository.currentSettings.stabilitySensitivity
        val stabilityIndex = (varX + varY) * sensitivity

        return StabilityResult(
            stabilityIndex = stabilityIndex,
            varianceX = varX,
            varianceY = varY,
            varianceZ = varZ
        )
    }

    /**
     * Classify stability level based on index.
     */
    fun classifyStability(stabilityIndex: Float): StabilityLevel {
        return when {
            stabilityIndex < STABILITY_EXCELLENT -> StabilityLevel.EXCELLENT
            stabilityIndex < STABILITY_GOOD -> StabilityLevel.GOOD
            stabilityIndex < STABILITY_MODERATE -> StabilityLevel.MODERATE
            else -> StabilityLevel.POOR
        }
    }
}

data class StabilityResult(
    val stabilityIndex: Float,
    val varianceX: Float,
    val varianceY: Float,
    val varianceZ: Float
)

enum class StabilityLevel {
    EXCELLENT, GOOD, MODERATE, POOR
}
