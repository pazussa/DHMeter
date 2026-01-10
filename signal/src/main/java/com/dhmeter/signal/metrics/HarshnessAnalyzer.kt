package com.dhmeter.signal.metrics

import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.signal.dsp.ButterworthFilter
import com.dhmeter.signal.dsp.LowPassFilter
import com.dhmeter.signal.dsp.SignalUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analyzes high-frequency vibration (chatter) in acceleration data.
 * Uses 15-40Hz bandpass filter to isolate vibration content.
 */
@Singleton
class HarshnessAnalyzer @Inject constructor() {

    companion object {
        const val LOW_CUTOFF_HZ = 15f
        const val HIGH_CUTOFF_HZ = 40f
        const val GRAVITY = 9.81f
    }

    /**
     * Analyze a window of accelerometer data and return harshness RMS.
     */
    fun analyzeWindow(samples: List<AccelSample>, sampleRate: Float): Float {
        if (samples.size < 10) return 0f

        // Create bandpass filter for this sample rate
        val filter = ButterworthFilter(
            order = 4,
            sampleRate = sampleRate,
            lowCutoff = LOW_CUTOFF_HZ,
            highCutoff = HIGH_CUTOFF_HZ
        )

        // Estimate gravity direction
        val gravityFilter = LowPassFilter(alpha = 0.1f)
        gravityFilter.reset()

        // Compute vertical acceleration and filter it
        val verticalAccel = samples.map { sample ->
            val gVec = gravityFilter.filter(sample.x, sample.y, sample.z)
            val gMag = sqrt(gVec[0].pow(2) + gVec[1].pow(2) + gVec[2].pow(2))
            if (gMag < 0.1f) sample.magnitude - GRAVITY
            else {
                val dot = (sample.x * gVec[0] + sample.y * gVec[1] + sample.z * gVec[2]) / gMag
                dot - GRAVITY
            }
        }

        // Apply bandpass filter
        filter.reset()
        val filtered = verticalAccel.map { filter.filter(it) }.toFloatArray()

        // Calculate RMS of filtered signal
        return SignalUtils.rms(filtered)
    }
}
