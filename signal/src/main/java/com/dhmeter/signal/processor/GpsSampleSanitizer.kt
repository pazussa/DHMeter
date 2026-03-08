package com.dhmeter.signal.processor

import com.dhmeter.sensing.data.GpsSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cleans GPS samples to reduce drift/jumps before distance mapping and map rendering.
 */
internal object GpsSampleSanitizer {

    private const val HARD_MAX_ACCURACY_M = 90f
    private const val HARD_MAX_SPEED_MPS = 35f
    private const val SPEED_HEADROOM_MPS = 6f
    private const val MIN_TIME_DELTA_SEC = 0.10f

    fun sanitize(samples: List<GpsSample>, gpsSensitivity: Float = 1f): List<GpsSample> {
        if (samples.isEmpty()) return emptyList()

        val maxAccuracyM = (35f / gpsSensitivity.coerceAtLeast(0.01f)).coerceIn(12f, 60f)
        val ordered = samples
            .asSequence()
            .filter { sample ->
                sample.latitude.isFinite() &&
                    sample.longitude.isFinite() &&
                    sample.accuracy.isFinite() &&
                    sample.accuracy > 0f &&
                    sample.accuracy <= HARD_MAX_ACCURACY_M
            }
            .sortedBy { it.timestampNs }
            .toList()
        if (ordered.size < 2) return ordered

        val filtered = ArrayList<GpsSample>(ordered.size)
        filtered += ordered.first()

        for (index in 1 until ordered.size) {
            val prev = filtered.last()
            val curr = ordered[index]
            val deltaTimeSec = ((curr.timestampNs - prev.timestampNs) / 1_000_000_000.0).toFloat()
            if (deltaTimeSec < MIN_TIME_DELTA_SEC) continue

            val segmentDistanceM = haversineDistance(
                prev.latitude,
                prev.longitude,
                curr.latitude,
                curr.longitude
            ).toFloat()
            val impliedSpeedMps = segmentDistanceM / deltaTimeSec
            val dynamicMaxSpeed = max(
                HARD_MAX_SPEED_MPS,
                max(prev.speed, curr.speed).coerceAtLeast(0f) + SPEED_HEADROOM_MPS +
                    max(prev.accuracy, curr.accuracy) / deltaTimeSec
            )
            val hasWeakAccuracy = prev.accuracy > maxAccuracyM || curr.accuracy > maxAccuracyM
            if (hasWeakAccuracy && impliedSpeedMps > dynamicMaxSpeed) continue

            filtered += curr
        }

        if (filtered.size < 2) {
            return ordered.takeLast(2)
        }

        return smoothByAccuracy(filtered, maxAccuracyM)
    }

    private fun smoothByAccuracy(samples: List<GpsSample>, maxAccuracyM: Float): List<GpsSample> {
        if (samples.size < 3) return samples

        val smoothed = ArrayList<GpsSample>(samples.size)
        var smoothLat = samples.first().latitude
        var smoothLon = samples.first().longitude
        smoothed += samples.first()

        for (index in 1 until samples.size) {
            val sample = samples[index]
            val normalizedAccuracy = (sample.accuracy / (maxAccuracyM * 1.35f)).coerceIn(0f, 1f)
            val alpha = (0.82f - normalizedAccuracy * 0.55f).coerceIn(0.24f, 0.85f)
            smoothLat += (sample.latitude - smoothLat) * alpha
            smoothLon += (sample.longitude - smoothLon) * alpha
            smoothed += sample.copy(latitude = smoothLat, longitude = smoothLon)
        }

        return smoothed
    }

    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
