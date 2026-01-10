package com.dhmeter.signal.metrics

import com.dhmeter.sensing.data.AccelSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects landing events from jumps or drops.
 * Landing is characterized by high acceleration spike.
 * Uses LINEAR_ACCELERATION which already has gravity removed.
 */
@Singleton
class LandingDetector @Inject constructor() {

    companion object {
        const val GRAVITY = 9.81f
        const val LANDING_THRESHOLD_G = 2.5f  // 2.5g threshold for landing (lowered for LINEAR_ACCEL)
        const val AIRTIME_THRESHOLD_G = 0.3f  // Below 0.3g considered airborne (LINEAR_ACCEL ~0 when falling)
        const val MIN_AIRTIME_MS = 100L       // Minimum airtime before landing
        const val DEBOUNCE_MS = 1000L         // 1 second between landings
        const val ENERGY_WINDOW_MS = 300L     // Window for energy calculation
    }

    /**
     * Detect landing events in a sequence of LINEAR_ACCELERATION samples.
     * Since LINEAR_ACCELERATION has gravity removed, magnitude ~0 during freefall.
     */
    fun detectLandings(
        samples: List<AccelSample>,
        sampleRate: Float
    ): List<LandingEvent> {
        if (samples.size < 20) return emptyList()

        val landings = mutableListOf<LandingEvent>()
        val samplesPerMs = sampleRate / 1000f
        val energyWindowSamples = (ENERGY_WINDOW_MS * samplesPerMs).toInt()
        val debouncesamples = (DEBOUNCE_MS * samplesPerMs).toInt()

        // Use magnitude directly for LINEAR_ACCELERATION (already gravity-free)
        val magnitudesG = samples.map { sample ->
            sqrt(sample.x.pow(2) + sample.y.pow(2) + sample.z.pow(2)) / GRAVITY
        }

        var lastLandingIdx = -debouncesamples
        var i = 0

        while (i < samples.size) {
            // Check for landing spike (high magnitude)
            if (i - lastLandingIdx > debouncesamples &&
                magnitudesG[i] > LANDING_THRESHOLD_G
            ) {
                // Look back for airtime (low magnitude = freefall in LINEAR_ACCEL)
                val lookbackSamples = (MIN_AIRTIME_MS * samplesPerMs).toInt()
                var airtimeFound = false
                var airtimeStart = -1

                for (j in (i - lookbackSamples - 50).coerceAtLeast(0) until i) {
                    if (magnitudesG[j] < AIRTIME_THRESHOLD_G) {
                        if (airtimeStart == -1) airtimeStart = j
                        val airtimeDuration = (i - airtimeStart) / samplesPerMs
                        if (airtimeDuration >= MIN_AIRTIME_MS) {
                            airtimeFound = true
                            break
                        }
                    } else {
                        airtimeStart = -1
                    }
                }

                if (airtimeFound) {
                    // Find peak G in landing window
                    val peakWindowEnd = (i + 10).coerceAtMost(samples.size)
                    var peakG = magnitudesG[i]
                    var peakIdx = i

                    for (j in i until peakWindowEnd) {
                        if (magnitudesG[j] > peakG) {
                            peakG = magnitudesG[j]
                            peakIdx = j
                        }
                    }

                    // Calculate energy in 300ms window after landing
                    val energyStart = peakIdx
                    val energyEnd = (peakIdx + energyWindowSamples).coerceAtMost(samples.size)
                    var energy = 0f
                    for (j in energyStart until energyEnd) {
                        energy += magnitudesG[j].pow(2)
                    }
                    energy = sqrt(energy / (energyEnd - energyStart).coerceAtLeast(1))

                    // Calculate recovery time (time to return to low magnitude)
                    var recoveryMs = 0L
                    for (j in peakIdx until samples.size) {
                        if (magnitudesG[j] < 0.5f) { // Returned to near-zero (stable)
                            recoveryMs = ((j - peakIdx) / samplesPerMs).toLong()
                            break
                        }
                    }

                    // Calculate airtime duration
                    val airtimeMs = if (airtimeStart >= 0) {
                        ((i - airtimeStart) / samplesPerMs).toLong()
                    } else 0L

                    landings.add(
                        LandingEvent(
                            timestampMs = samples[peakIdx].timestampNs / 1_000_000,
                            peakG = peakG,
                            energy300ms = energy,
                            recoveryMs = recoveryMs,
                            airtimeMs = airtimeMs
                        )
                    )

                    lastLandingIdx = peakIdx
                    i = peakIdx + debouncesamples
                    continue
                }
            }
            i++
        }

        return landings
    }

    /**
     * Classify landing quality based on metrics.
     */
    fun classifyLanding(event: LandingEvent): LandingQuality {
        return when {
            event.peakG > 5f || event.recoveryMs > 500L -> LandingQuality.HARSH
            event.peakG > 4f || event.recoveryMs > 300L -> LandingQuality.MODERATE
            event.recoveryMs < 200L && event.peakG < 4f -> LandingQuality.SMOOTH
            else -> LandingQuality.MODERATE
        }
    }
}

data class LandingEvent(
    val timestampMs: Long,
    val peakG: Float,
    val energy300ms: Float,
    val recoveryMs: Long,
    val airtimeMs: Long
)

enum class LandingQuality {
    SMOOTH, MODERATE, HARSH
}
