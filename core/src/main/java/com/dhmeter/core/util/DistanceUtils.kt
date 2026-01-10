package com.dhmeter.core.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility functions for distance and coordinate calculations.
 */
object DistanceUtils {

    private const val EARTH_RADIUS_M = 6371000.0

    /**
     * Calculate distance between two GPS coordinates using Haversine formula.
     * @return Distance in meters
     */
    fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_M * c
    }

    /**
     * Format distance in meters to human readable string.
     * e.g., "1.5 km" or "850 m"
     */
    fun formatDistance(meters: Float): String {
        return when {
            meters >= 1000 -> String.format("%.1f km", meters / 1000)
            meters >= 100 -> String.format("%.0f m", meters)
            else -> String.format("%.1f m", meters)
        }
    }

    /**
     * Format distance with more precision.
     */
    fun formatDistancePrecise(meters: Float): String {
        return when {
            meters >= 1000 -> String.format("%.2f km", meters / 1000)
            else -> String.format("%.1f m", meters)
        }
    }

    /**
     * Calculate bearing between two points.
     * @return Bearing in degrees (0-360)
     */
    fun bearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        var bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Convert meters per second to km/h.
     */
    fun mpsToKmh(mps: Float): Float = mps * 3.6f

    /**
     * Convert km/h to meters per second.
     */
    fun kmhToMps(kmh: Float): Float = kmh / 3.6f

    /**
     * Format speed in m/s to km/h string.
     */
    fun formatSpeed(mps: Float): String {
        val kmh = mpsToKmh(mps)
        return String.format("%.1f km/h", kmh)
    }

    /**
     * Format elevation in meters.
     */
    fun formatElevation(meters: Float): String {
        return String.format("%.0f m", meters)
    }

    /**
     * Format slope percentage.
     */
    fun formatSlope(percent: Float): String {
        return String.format("%.1f%%", percent)
    }

    /**
     * Calculate percentage along a track.
     */
    fun calculatePercentage(currentDistance: Float, totalDistance: Float): Float {
        if (totalDistance <= 0) return 0f
        return (currentDistance / totalDistance * 100).coerceIn(0f, 100f)
    }
}
