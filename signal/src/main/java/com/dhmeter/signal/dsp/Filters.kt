package com.dhmeter.signal.dsp

import kotlin.math.*

/**
 * IIR Butterworth filter implementation.
 */
class ButterworthFilter(
    private val order: Int,
    private val sampleRate: Float,
    private val lowCutoff: Float,
    private val highCutoff: Float
) {
    private val a: DoubleArray
    private val b: DoubleArray
    private val z: DoubleArray // Filter state

    init {
        // Design bandpass Butterworth filter
        val coeffs = designBandpass(order, sampleRate.toDouble(), lowCutoff.toDouble(), highCutoff.toDouble())
        b = coeffs.first
        a = coeffs.second
        z = DoubleArray(maxOf(a.size, b.size))
    }

    /**
     * Filter a single sample (direct form II transposed)
     */
    fun filter(x: Float): Float {
        val y = b[0] * x + z[0]
        for (i in 1 until z.size) {
            z[i - 1] = b.getOrElse(i) { 0.0 } * x - a.getOrElse(i) { 0.0 } * y + z.getOrElse(i) { 0.0 }
        }
        return y.toFloat()
    }

    /**
     * Filter an array of samples
     */
    fun filter(input: FloatArray): FloatArray {
        return input.map { filter(it) }.toFloatArray()
    }

    /**
     * Reset filter state
     */
    fun reset() {
        z.fill(0.0)
    }

    companion object {
        /**
         * Design bandpass Butterworth filter coefficients.
         * Simplified implementation for 4th order.
         */
        fun designBandpass(
            order: Int,
            fs: Double,
            lowFreq: Double,
            highFreq: Double
        ): Pair<DoubleArray, DoubleArray> {
            // Normalized frequencies
            val nyq = fs / 2.0
            val low = lowFreq / nyq
            val high = highFreq / nyq

            // Pre-warping
            val lowW = tan(PI * low)
            val highW = tan(PI * high)
            val bw = highW - lowW
            val w0 = sqrt(lowW * highW)

            // 2nd order bandpass section (simplified for MVP)
            // This is a simplified version - production would use scipy-like design
            val Q = w0 / bw
            val alpha = sin(2 * PI * (low + high) / 2) / (2 * Q)

            val b0 = alpha
            val b1 = 0.0
            val b2 = -alpha
            val a0 = 1.0 + alpha
            val a1 = -2.0 * cos(2 * PI * (low + high) / 2)
            val a2 = 1.0 - alpha

            // Normalize
            val b = doubleArrayOf(b0 / a0, b1 / a0, b2 / a0)
            val a = doubleArrayOf(1.0, a1 / a0, a2 / a0)

            return Pair(b, a)
        }
    }
}

/**
 * Simple moving average filter
 */
class MovingAverageFilter(private val windowSize: Int) {
    private val buffer = ArrayDeque<Float>(windowSize)
    private var sum = 0f

    fun filter(x: Float): Float {
        sum += x
        buffer.addLast(x)
        
        if (buffer.size > windowSize) {
            sum -= buffer.removeFirst()
        }
        
        return sum / buffer.size
    }

    fun reset() {
        buffer.clear()
        sum = 0f
    }
}

/**
 * Low-pass filter for gravity estimation
 */
class LowPassFilter(private val alpha: Float = 0.1f) {
    private var lastOutput = floatArrayOf(0f, 0f, 0f)
    private var initialized = false

    fun filter(x: Float, y: Float, z: Float): FloatArray {
        if (!initialized) {
            lastOutput = floatArrayOf(x, y, z)
            initialized = true
            return lastOutput.copyOf()
        }

        lastOutput[0] = alpha * x + (1 - alpha) * lastOutput[0]
        lastOutput[1] = alpha * y + (1 - alpha) * lastOutput[1]
        lastOutput[2] = alpha * z + (1 - alpha) * lastOutput[2]

        return lastOutput.copyOf()
    }

    fun reset() {
        lastOutput = floatArrayOf(0f, 0f, 0f)
        initialized = false
    }
}
