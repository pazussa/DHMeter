package com.dhmeter.signal.processor

import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsPolyline
import com.dhmeter.domain.model.MapGpsQuality
import com.dhmeter.sensing.data.GpsSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Processes GPS samples into a shape-preserving polyline for map visualization.
 */
@Singleton
class GpsPolylineProcessor @Inject constructor() {

    companion object {
        const val MAX_POINTS = 150
        private const val MIN_POINT_SPACING_M = 1.5
        private const val GOOD_ACCURACY_THRESHOLD = 15f // < 15m = GOOD
        private const val OK_ACCURACY_THRESHOLD = 30f   // 15-30m = OK, > 30m = POOR
    }

    /**
     * Process raw GPS samples into a simplified polyline.
     */
    fun processToPolyline(
        runId: String,
        samples: List<GpsSample>,
        totalDistanceM: Float
    ): GpsPolyline {
        val normalizedSamples = normalizeSamples(samples)
        if (normalizedSamples.isEmpty()) {
            return GpsPolyline(
                runId = runId,
                points = emptyList(),
                totalDistanceM = 0f,
                avgAccuracyM = 0f,
                gpsQuality = MapGpsQuality.POOR
            )
        }

        val distancedSamples = calculateCumulativeDistances(normalizedSamples)
        val simplified = simplifyPolyline(distancedSamples, MAX_POINTS)

        val points = simplified.map { (sample, cumulativeDist) ->
            GpsPoint(
                lat = sample.latitude,
                lon = sample.longitude,
                distPct = if (totalDistanceM > 0f) {
                    (cumulativeDist / totalDistanceM * 100f).coerceIn(0f, 100f)
                } else 0f,
                altitudeM = sample.altitude?.toFloat()
            )
        }

        val avgAccuracy = normalizedSamples.map { it.accuracy }.average().toFloat()
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

    private fun normalizeSamples(samples: List<GpsSample>): List<GpsSample> {
        val ordered = samples
            .asSequence()
            .filter {
                it.latitude.isFinite() &&
                    it.longitude.isFinite() &&
                    it.accuracy.isFinite() &&
                    it.accuracy > 0f
            }
            .sortedBy { it.timestampNs }
            .toList()
        if (ordered.size < 2) return ordered

        val deduped = ArrayList<GpsSample>(ordered.size)
        ordered.forEach { sample ->
            val previous = deduped.lastOrNull()
            if (previous == null || previous.timestampNs != sample.timestampNs) {
                deduped += sample
            } else if (sample.accuracy < previous.accuracy) {
                deduped[deduped.lastIndex] = sample
            }
        }

        return deduped
    }

    /**
     * Calculate cumulative distance for each sample.
     */
    private fun calculateCumulativeDistances(samples: List<GpsSample>): List<Pair<GpsSample, Float>> {
        if (samples.isEmpty()) return emptyList()

        val result = ArrayList<Pair<GpsSample, Float>>(samples.size)
        var cumulativeDistance = 0f
        result += samples.first() to 0f

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]
            val segmentDist = haversineDistance(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            cumulativeDistance += segmentDist
            result += curr to cumulativeDistance
        }

        return result
    }

    /**
     * Simplify polyline preserving geometry using Ramer-Douglas-Peucker.
     */
    private fun simplifyPolyline(
        samples: List<Pair<GpsSample, Float>>,
        maxPoints: Int
    ): List<Pair<GpsSample, Float>> {
        if (samples.size <= 2) return samples

        val spaced = keepMinimumSpacing(samples, MIN_POINT_SPACING_M)
        if (spaced.size <= 2) return listOf(samples.first(), samples.last())
        if (spaced.size <= maxPoints) return spaced

        val avgAccuracy = spaced.map { it.first.accuracy }.average().toFloat()
        val epsilonM = (avgAccuracy * 0.30f).coerceIn(2f, 9f).toDouble()
        val keepIndices = rdpKeepIndices(spaced.map { it.first }, epsilonM)
        val reduced = keepIndices.sorted().map { index -> spaced[index] }

        if (reduced.size <= maxPoints) {
            return reduced
        }

        // Hard cap preserving start/end and regular spacing.
        val downsampled = ArrayList<Pair<GpsSample, Float>>(maxPoints)
        val step = (reduced.lastIndex.toDouble() / (maxPoints - 1).coerceAtLeast(1).toDouble())
        for (i in 0 until maxPoints) {
            val index = (i * step).toInt().coerceIn(0, reduced.lastIndex)
            val candidate = reduced[index]
            if (downsampled.lastOrNull() != candidate) {
                downsampled += candidate
            }
        }
        if (downsampled.lastOrNull() != reduced.last()) {
            downsampled += reduced.last()
        }
        return downsampled
    }

    private fun keepMinimumSpacing(
        samples: List<Pair<GpsSample, Float>>,
        minSpacingM: Double
    ): List<Pair<GpsSample, Float>> {
        if (samples.size <= 2) return samples

        val kept = ArrayList<Pair<GpsSample, Float>>(samples.size)
        kept += samples.first()

        for (index in 1 until samples.lastIndex) {
            val lastKept = kept.last().first
            val current = samples[index].first
            val distance = haversineDistance(
                lastKept.latitude,
                lastKept.longitude,
                current.latitude,
                current.longitude
            )
            if (distance >= minSpacingM) {
                kept += samples[index]
            }
        }

        if (kept.lastOrNull() != samples.last()) {
            kept += samples.last()
        }
        return kept
    }

    private fun rdpKeepIndices(points: List<GpsSample>, epsilonM: Double): Set<Int> {
        val keep = linkedSetOf(0, points.lastIndex)
        fun recurse(start: Int, end: Int) {
            if (end <= start + 1) return

            val startPoint = points[start]
            val endPoint = points[end]
            var maxDistance = -1.0
            var maxIndex = -1

            for (index in (start + 1) until end) {
                val distance = perpendicularDistanceMeters(points[index], startPoint, endPoint)
                if (distance > maxDistance) {
                    maxDistance = distance
                    maxIndex = index
                }
            }

            if (maxDistance > epsilonM && maxIndex > start && maxIndex < end) {
                keep += maxIndex
                recurse(start, maxIndex)
                recurse(maxIndex, end)
            }
        }

        recurse(0, points.lastIndex)
        return keep
    }

    private fun perpendicularDistanceMeters(
        point: GpsSample,
        lineStart: GpsSample,
        lineEnd: GpsSample
    ): Double {
        val referenceLatRad = Math.toRadians((lineStart.latitude + lineEnd.latitude) / 2.0)
        val earthRadius = 6_371_000.0

        fun project(sample: GpsSample): Pair<Double, Double> {
            val x = Math.toRadians(sample.longitude) * cos(referenceLatRad) * earthRadius
            val y = Math.toRadians(sample.latitude) * earthRadius
            return x to y
        }

        val (x0, y0) = project(point)
        val (x1, y1) = project(lineStart)
        val (x2, y2) = project(lineEnd)

        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) {
            return sqrt((x0 - x1).pow(2) + (y0 - y1).pow(2))
        }

        val t = (((x0 - x1) * dx + (y0 - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val projectionX = x1 + t * dx
        val projectionY = y1 + t * dy

        return sqrt((x0 - projectionX).pow(2) + (y0 - projectionY).pow(2))
    }

    /**
     * Haversine distance between two points in meters.
     */
    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }
}
