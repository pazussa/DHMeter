package com.dhmeter.signal.processor

import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsPolyline
import com.dhmeter.domain.model.MapGpsQuality
import com.dhmeter.sensing.data.GpsSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Processes GPS samples into a simplified polyline for map visualization.
 * - Simplifies to max ~150 points
 * - Calculates distance percentage for each point
 * - Determines GPS quality
 */
@Singleton
class GpsPolylineProcessor @Inject constructor() {
    
    companion object {
        const val MAX_POINTS = 150
        const val MIN_DISTANCE_BETWEEN_POINTS_M = 10.0 // Minimum 10m between points
        const val GOOD_ACCURACY_THRESHOLD = 15f // < 15m = GOOD
        const val OK_ACCURACY_THRESHOLD = 30f   // 15-30m = OK, > 30m = POOR
    }
    
    /**
     * Process raw GPS samples into a simplified polyline.
     */
    fun processToPolyline(
        runId: String,
        samples: List<GpsSample>,
        totalDistanceM: Float
    ): GpsPolyline {
        if (samples.isEmpty()) {
            return GpsPolyline(
                runId = runId,
                points = emptyList(),
                totalDistanceM = 0f,
                avgAccuracyM = 0f,
                gpsQuality = MapGpsQuality.POOR
            )
        }
        
        // Calculate cumulative distances
        val distancedSamples = calculateCumulativeDistances(samples)
        
        // Simplify to max points
        val simplified = simplifyPolyline(distancedSamples, MAX_POINTS)
        
        // Convert to GpsPoints with distance percentage
        val points = simplified.map { (sample, cumulativeDist) ->
            GpsPoint(
                lat = sample.latitude,
                lon = sample.longitude,
                distPct = if (totalDistanceM > 0) {
                    (cumulativeDist / totalDistanceM * 100f).coerceIn(0f, 100f)
                } else 0f
            )
        }
        
        // Calculate GPS quality
        val avgAccuracy = samples.map { it.accuracy }.average().toFloat()
        val quality = when {
            avgAccuracy < GOOD_ACCURACY_THRESHOLD -> MapGpsQuality.GOOD
            avgAccuracy < OK_ACCURACY_THRESHOLD -> MapGpsQuality.OK
            else -> MapGpsQuality.POOR
        }
        
        return GpsPolyline(
            runId = runId,
            points = points,
            totalDistanceM = totalDistanceM,
            avgAccuracyM = avgAccuracy,
            gpsQuality = quality
        )
    }
    
    /**
     * Calculate cumulative distance for each sample.
     */
    private fun calculateCumulativeDistances(samples: List<GpsSample>): List<Pair<GpsSample, Float>> {
        if (samples.isEmpty()) return emptyList()
        
        val result = mutableListOf<Pair<GpsSample, Float>>()
        var cumulativeDistance = 0f
        
        result.add(samples.first() to 0f)
        
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val segmentDist = haversineDistance(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )
            cumulativeDistance += segmentDist
            result.add(curr to cumulativeDistance)
        }
        
        return result
    }
    
    /**
     * Simplify polyline using distance-based sampling.
     * Takes approximately evenly spaced points by distance.
     */
    private fun simplifyPolyline(
        samples: List<Pair<GpsSample, Float>>,
        maxPoints: Int
    ): List<Pair<GpsSample, Float>> {
        if (samples.size <= maxPoints) return samples
        
        val result = mutableListOf<Pair<GpsSample, Float>>()
        
        // Always include first point
        result.add(samples.first())
        
        val totalDist = samples.last().second
        if (totalDist <= 0) return listOf(samples.first(), samples.last())
        
        // Target distance between points
        val targetSpacing = totalDist / (maxPoints - 1)
        var lastAddedDist = 0f
        
        for (i in 1 until samples.size - 1) {
            val (_, dist) = samples[i]
            if (dist - lastAddedDist >= targetSpacing) {
                result.add(samples[i])
                lastAddedDist = dist
            }
        }
        
        // Always include last point
        if (samples.size > 1) {
            result.add(samples.last())
        }
        
        return result
    }
    
    /**
     * Haversine distance between two points in meters.
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val R = 6371000.0 // Earth radius in meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return (R * c).toFloat()
    }
}
