package com.dhmeter.signal.processor

import com.dhmeter.sensing.data.GpsSample
import javax.inject.Inject
import kotlin.math.*

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
        
        // Find surrounding GPS samples by timestamp
        var lowIdx = 0
        for (i in samples.indices) {
            if (samples[i].timestampNs <= timestampNs) lowIdx = i
        }
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
}

/**
 * Creates distance mapping from GPS samples.
 */
class DistanceMapper @Inject constructor() {
    fun createMapping(samples: List<GpsSample>, startTimeNs: Long): DistanceMapping {
        if (samples.isEmpty()) {
            return DistanceMapping(emptyList(), startTimeNs, 0.0)
        }

        // Calculate total distance
        var totalDistance = 0.0
        for (i in 1 until samples.size) {
            totalDistance += DistanceMapping.haversineDistance(
                samples[i - 1].latitude, samples[i - 1].longitude,
                samples[i].latitude, samples[i].longitude
            )
        }

        return DistanceMapping(samples, startTimeNs, totalDistance)
    }
}
