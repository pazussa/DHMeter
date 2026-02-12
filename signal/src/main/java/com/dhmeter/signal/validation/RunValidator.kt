package com.dhmeter.signal.validation

import com.dhmeter.domain.model.GpsQuality
import com.dhmeter.domain.model.InvalidRunReason
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.sensing.data.GpsSample
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates run data quality based on GPS + movement (no barometer).
 * MVP2: Validates continuous DH-style movement without altitude sensor.
 */
@Singleton
class RunValidator @Inject constructor(
    private val sensitivityRepository: SensorSensitivityRepository
) {

    companion object {
        // Validation thresholds (MVP2 - GPS/movement based)
        const val MIN_DURATION_MS = 30_000L         // 30 seconds minimum
        const val MAX_DURATION_MS = 3_600_000L      // 1 hour maximum
        const val MIN_DISTANCE_M = 250f             // 250 meters minimum (DH descent)
        const val MIN_GPS_SAMPLES = 10              // At least 10 GPS points
        const val MAX_GPS_ACCURACY_M = 25f          // Maximum acceptable GPS accuracy
        const val MIN_GOOD_GPS_RATIO = 0.7f         // 70% of GPS samples must be good
        const val MIN_SIGNAL_QUALITY = 0.6f         // 60% signal quality threshold
        
        // Movement validation (replaces barometer descent check)
        const val MIN_SPEED_THRESHOLD_MPS = 2.22f   // 8 km/h in m/s
        const val MIN_MOVING_RATIO = 0.7f           // 70% of time with speed > 8 km/h
        const val MAX_PAUSE_DURATION_MS = 2000L     // Max 2s pause with speed < 2 km/h
        const val PAUSE_SPEED_THRESHOLD_MPS = 0.56f // 2 km/h considered pause
    }

    /**
     * Validate a complete run and determine if it should be saved.
     * MVP2: No barometer - uses GPS speed/movement continuity.
     */
    fun validateRun(
        accelSamples: List<AccelSample>,
        gpsSamples: List<GpsSample>,
        durationMs: Long,
        totalDistanceM: Float
    ): ValidationResult {
        val issues = mutableListOf<InvalidRunReason>()
        val warnings = mutableListOf<String>()

        // Check duration
        if (durationMs < MIN_DURATION_MS) {
            issues.add(InvalidRunReason.TOO_SHORT)
        }
        if (durationMs > MAX_DURATION_MS) {
            issues.add(InvalidRunReason.TOO_LONG)
        }

        // Check distance
        if (totalDistanceM < MIN_DISTANCE_M) {
            issues.add(InvalidRunReason.NO_MOVEMENT)
        }

        // Check GPS quality
        val gpsQuality = validateGpsQuality(gpsSamples)
        if (gpsQuality.overallQuality == GpsQuality.POOR) {
            issues.add(InvalidRunReason.GPS_POOR)
        }

        // Check movement continuity (replaces descent check)
        val movementValidation = validateMovementContinuity(gpsSamples)
        if (!movementValidation.isValidMovement) {
            issues.add(InvalidRunReason.NO_MOVEMENT)
        }
        if (movementValidation.hasLongPauses) {
            warnings.add("Long pauses detected during run")
        }

        // Check signal quality (accelerometer data stability)
        val signalQuality = validateSignalQuality(accelSamples)
        if (signalQuality < MIN_SIGNAL_QUALITY) {
            issues.add(InvalidRunReason.POOR_SIGNAL)
        }

        // Check average speed
        val avgSpeedMps = if (durationMs > 0) totalDistanceM / (durationMs / 1000f) else 0f
        
        // Determine validity
        val isValid = issues.isEmpty()

        return ValidationResult(
            isValid = isValid,
            issues = issues,
            warnings = warnings,
            gpsQuality = gpsQuality,
            signalQuality = signalQuality,
            movementValidation = movementValidation,
            avgSpeedMps = avgSpeedMps
        )
    }

    /**
     * Validate movement continuity - replaces barometer descent validation.
     * Checks that run has continuous DH-style movement.
     */
    private fun validateMovementContinuity(samples: List<GpsSample>): MovementValidation {
        if (samples.size < MIN_GPS_SAMPLES) {
            return MovementValidation(
                isValidMovement = false,
                movingRatio = 0f,
                hasLongPauses = true,
                maxPauseDurationMs = Long.MAX_VALUE
            )
        }

        var movingSamples = 0
        var maxPauseDurationMs = 0L
        var currentPauseStartNs: Long? = null

        for (i in samples.indices) {
            val sample = samples[i]
            
            if (sample.speed >= MIN_SPEED_THRESHOLD_MPS) {
                movingSamples++
                // End any current pause
                if (currentPauseStartNs != null) {
                    val pauseDuration = (sample.timestampNs - currentPauseStartNs) / 1_000_000
                    if (pauseDuration > maxPauseDurationMs) {
                        maxPauseDurationMs = pauseDuration
                    }
                    currentPauseStartNs = null
                }
            } else if (sample.speed < PAUSE_SPEED_THRESHOLD_MPS) {
                // Start tracking pause if not already
                if (currentPauseStartNs == null) {
                    currentPauseStartNs = sample.timestampNs
                }
            }
        }

        // Check if pause extends to end
        if (currentPauseStartNs != null && samples.isNotEmpty()) {
            val pauseDuration = (samples.last().timestampNs - currentPauseStartNs) / 1_000_000
            if (pauseDuration > maxPauseDurationMs) {
                maxPauseDurationMs = pauseDuration
            }
        }

        val movingRatio = movingSamples.toFloat() / samples.size
        val hasLongPauses = maxPauseDurationMs > MAX_PAUSE_DURATION_MS
        val isValidMovement = movingRatio >= MIN_MOVING_RATIO && !hasLongPauses

        return MovementValidation(
            isValidMovement = isValidMovement,
            movingRatio = movingRatio,
            hasLongPauses = hasLongPauses,
            maxPauseDurationMs = maxPauseDurationMs
        )
    }

    /**
     * Validate GPS data quality.
     */
    fun validateGpsQuality(samples: List<GpsSample>): GpsValidation {
        if (samples.size < MIN_GPS_SAMPLES) {
            return GpsValidation(
                overallQuality = GpsQuality.POOR,
                goodSampleRatio = 0f,
                averageAccuracyM = Float.MAX_VALUE,
                maxGapMs = Long.MAX_VALUE
            )
        }

        val maxGpsAccuracy = adjustedMaxGpsAccuracy()
        val minGoodGpsRatio = adjustedMinGoodGpsRatio()

        // Count good samples (accuracy < threshold)
        val goodSamples = samples.count { it.accuracy < maxGpsAccuracy }
        val goodRatio = goodSamples.toFloat() / samples.size

        // Calculate average accuracy
        val avgAccuracy = samples.map { it.accuracy }.average().toFloat()

        // Find maximum gap between samples (convert ns to ms)
        var maxGap = 0L
        for (i in 1 until samples.size) {
            val gap = (samples[i].timestampNs - samples[i - 1].timestampNs) / 1_000_000
            if (gap > maxGap) maxGap = gap
        }

        val quality = when {
            goodRatio >= 0.9f && avgAccuracy < 10f -> GpsQuality.EXCELLENT
            goodRatio >= minGoodGpsRatio && avgAccuracy < maxGpsAccuracy -> GpsQuality.GOOD
            goodRatio >= 0.5f -> GpsQuality.FAIR
            else -> GpsQuality.POOR
        }

        return GpsValidation(
            overallQuality = quality,
            goodSampleRatio = goodRatio,
            averageAccuracyM = avgAccuracy,
            maxGapMs = maxGap
        )
    }

    /**
     * Validate accelerometer signal quality.
     */
    fun validateSignalQuality(samples: List<AccelSample>): Float {
        if (samples.size < 100) return 0f

        var qualityScore = 1f

        // Check for data gaps
        val avgInterval = samples.zipWithNext { a, b -> b.timestampNs - a.timestampNs }.average()
        val gaps = samples.zipWithNext { a, b -> b.timestampNs - a.timestampNs }
        val largeGaps = gaps.count { it > avgInterval * 3 }
        val gapPenalty = (largeGaps.toFloat() / gaps.size).coerceIn(0f, 0.3f)
        qualityScore -= gapPenalty

        // Check signal variance (should not be zero or extremely high)
        val magnitudes = samples.map { it.magnitude }
        val meanMag = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - meanMag) * (it - meanMag) }.average().toFloat()

        if (variance < 0.01f) {
            qualityScore -= 0.3f
        }
        if (variance > 1000f) {
            qualityScore -= 0.2f
        }

        // Check for stuck/frozen sensor
        val uniqueValues = samples.map { "${it.x.toInt()}-${it.y.toInt()}-${it.z.toInt()}" }.toSet()
        if (uniqueValues.size < samples.size / 10) {
            qualityScore -= 0.4f
        }

        return qualityScore.coerceIn(0f, 1f)
    }

    /**
     * Check if user is currently moving (for live monitoring).
     */
    fun isMoving(gpsSamples: List<GpsSample>, windowSizeMs: Long = 3000L): Boolean {
        if (gpsSamples.isEmpty()) return false

        val cutoffTimeNs = gpsSamples.last().timestampNs - (windowSizeMs * 1_000_000)
        val recentSamples = gpsSamples.filter { it.timestampNs >= cutoffTimeNs }
        if (recentSamples.isEmpty()) return false

        val avgSpeed = recentSamples.map { it.speed }.average()
        return avgSpeed >= MIN_SPEED_THRESHOLD_MPS
    }

    private fun adjustedMaxGpsAccuracy(): Float {
        val gpsSensitivity = sensitivityRepository.currentSettings.gpsSensitivity
        return (MAX_GPS_ACCURACY_M / gpsSensitivity.coerceAtLeast(0.01f))
            .coerceIn(10f, 60f)
    }

    private fun adjustedMinGoodGpsRatio(): Float {
        val gpsSensitivity = sensitivityRepository.currentSettings.gpsSensitivity
        return (MIN_GOOD_GPS_RATIO + (gpsSensitivity - 1f) * 0.2f)
            .coerceIn(0.5f, 0.95f)
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val issues: List<InvalidRunReason>,
    val warnings: List<String>,
    val gpsQuality: GpsValidation,
    val signalQuality: Float,
    val movementValidation: MovementValidation,
    val avgSpeedMps: Float
)

data class GpsValidation(
    val overallQuality: GpsQuality,
    val goodSampleRatio: Float,
    val averageAccuracyM: Float,
    val maxGapMs: Long
)

data class MovementValidation(
    val isValidMovement: Boolean,
    val movingRatio: Float,
    val hasLongPauses: Boolean,
    val maxPauseDurationMs: Long
)
