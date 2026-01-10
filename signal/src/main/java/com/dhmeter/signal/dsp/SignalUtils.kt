package com.dhmeter.signal.dsp

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility functions for signal processing.
 */
object SignalUtils {

    /**
     * Calculate RMS (Root Mean Square) of a signal
     */
    fun rms(signal: FloatArray): Float {
        if (signal.isEmpty()) return 0f
        val sumSquares = signal.sumOf { (it * it).toDouble() }
        return sqrt(sumSquares / signal.size).toFloat()
    }

    /**
     * Calculate variance of a signal
     */
    fun variance(signal: FloatArray): Float {
        if (signal.isEmpty()) return 0f
        val mean = signal.average()
        return signal.sumOf { (it - mean).pow(2) }.toFloat() / signal.size
    }

    /**
     * Calculate percentile of a signal
     */
    fun percentile(signal: FloatArray, p: Float): Float {
        if (signal.isEmpty()) return 0f
        val sorted = signal.sortedArray()
        val index = (p / 100f * (sorted.size - 1)).toInt()
        return sorted[index.coerceIn(0, sorted.lastIndex)]
    }

    /**
     * Detect peaks in a signal with minimum distance between peaks.
     */
    fun findPeaks(
        signal: FloatArray,
        threshold: Float,
        minDistanceSamples: Int
    ): List<Int> {
        val peaks = mutableListOf<Int>()
        var lastPeakIdx = -minDistanceSamples

        for (i in 1 until signal.size - 1) {
            if (signal[i] > threshold &&
                signal[i] > signal[i - 1] &&
                signal[i] > signal[i + 1] &&
                (i - lastPeakIdx) >= minDistanceSamples
            ) {
                peaks.add(i)
                lastPeakIdx = i
            }
        }

        return peaks
    }

    /**
     * Normalize array values to 0-1 range
     */
    fun normalize(signal: FloatArray): FloatArray {
        if (signal.isEmpty()) return signal
        val min = signal.minOrNull() ?: 0f
        val max = signal.maxOrNull() ?: 1f
        val range = max - min
        if (range == 0f) return FloatArray(signal.size) { 0.5f }
        return signal.map { (it - min) / range }.toFloatArray()
    }

    /**
     * Downsample signal to target number of points
     */
    fun downsample(signal: FloatArray, targetPoints: Int): FloatArray {
        if (signal.size <= targetPoints) return signal.copyOf()
        
        val result = FloatArray(targetPoints)
        val ratio = signal.size.toFloat() / targetPoints
        
        for (i in 0 until targetPoints) {
            val startIdx = (i * ratio).toInt()
            val endIdx = ((i + 1) * ratio).toInt().coerceAtMost(signal.size)
            // Take average of samples in window
            result[i] = signal.slice(startIdx until endIdx).average().toFloat()
        }
        
        return result
    }

    /**
     * Interpolate signal to target number of points
     */
    fun interpolate(signal: FloatArray, targetPoints: Int): FloatArray {
        if (signal.size == targetPoints) return signal.copyOf()
        if (signal.isEmpty()) return FloatArray(targetPoints)
        
        val result = FloatArray(targetPoints)
        val ratio = (signal.size - 1).toFloat() / (targetPoints - 1)
        
        for (i in 0 until targetPoints) {
            val srcIdx = i * ratio
            val lowIdx = srcIdx.toInt().coerceIn(0, signal.size - 2)
            val fraction = srcIdx - lowIdx
            result[i] = signal[lowIdx] * (1 - fraction) + signal[lowIdx + 1] * fraction
        }
        
        return result
    }
}
