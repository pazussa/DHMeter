package com.dhmeter.signal.processor

import com.dhmeter.sensing.data.GpsSample
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maps timestamps to distance percentage along the track.
 */
class DistanceMapping(
    private val samples: List<GpsSample>,
    private val startTimeNs: Long,
    val totalDistance: Double
) {
    // Precomputed cumulative distances
    private val cumulativeDistances: List<Double>
    
    init {
        val distances = mutableListOf(0.0)
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val segmentDist = haversineDistance(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )
            distances.add(distances.last() + segmentDist)
        }
        cumulativeDistances = distances
    }

    /**
     * Get distance percentage (0-100) for a given timestamp.
     */
    fun getDistPct(timestampNs: Long): Float {
        if (samples.isEmpty() || totalDistance == 0.0) return 0f

        val lowIdx = findFloorIndex(timestampNs)
        val highIdx = (lowIdx + 1).coerceAtMost(samples.lastIndex)

        if (lowIdx == highIdx) {
            return ((cumulativeDistances[lowIdx] / totalDistance) * 100).toFloat()
        }

        // Interpolate
        val fraction = (timestampNs - samples[lowIdx].timestampNs).toDouble() /
                       (samples[highIdx].timestampNs - samples[lowIdx].timestampNs)

        val lowDist = cumulativeDistances[lowIdx]
        val highDist = cumulativeDistances[highIdx]
        val interpolatedDist = lowDist + fraction * (highDist - lowDist)

        return ((interpolatedDist / totalDistance) * 100).toFloat().coerceIn(0f, 100f)
    }

    /**
     * Get absolute distance (meters) for a given timestamp.
     */
    fun getDistance(timestampNs: Long): Double {
        return getDistPct(timestampNs) / 100.0 * totalDistance
    }

    companion object {
        fun haversineDistance(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val R = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }
    }

    private fun findFloorIndex(timestampNs: Long): Int {
        var low = 0
        var high = samples.lastIndex
        var floor = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = samples[mid].timestampNs
            if (midTime <= timestampNs) {
                floor = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return floor
    }
}

/**
 * Creates distance mapping from GPS samples.
 */
class DistanceMapper @Inject constructor() {
    companion object {
        private const val DIST_MIN_SPEED_MPS = 0.5f
        private const val DIST_MAX_ACCURACY_BASE_M = 20f
        private const val DIST_MIN_DISTANCE_FACTOR = 0.5f
    }

    fun createMapping(
        samples: List<GpsSample>,
        startTimeNs: Long,
        gpsSensitivity: Float = 1f
    ): DistanceMapping {
        val sanitized = GpsSampleSanitizer.sanitize(samples, gpsSensitivity)
        if (sanitized.size < 2) {
            return DistanceMapping(emptyList(), startTimeNs, 0.0)
        }

        // Calculate total distance
        var totalDistance = 0.0
        for (i in 1 until sanitized.size) {
            val previous = sanitized[i - 1]
            val current = sanitized[i]
            val segmentDistance = DistanceMapping.haversineDistance(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude
            )
            if (isValidDistanceSegment(previous, current, segmentDistance, gpsSensitivity)) {
                totalDistance += segmentDistance
            }
        }

        return DistanceMapping(sanitized, startTimeNs, totalDistance)
    }

    private fun isValidDistanceSegment(
        previous: GpsSample,
        current: GpsSample,
        segmentDistanceM: Double,
        gpsSensitivity: Float
    ): Boolean {
        val maxAccuracyM = (DIST_MAX_ACCURACY_BASE_M / gpsSensitivity.coerceAtLeast(0.01f)).coerceIn(10f, 60f)
        return current.accuracy <= maxAccuracyM &&
            current.speed >= DIST_MIN_SPEED_MPS &&
            segmentDistanceM > (max(previous.accuracy, current.accuracy) * DIST_MIN_DISTANCE_FACTOR)
    }
}
