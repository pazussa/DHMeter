package com.dhmeter.sensing.collector

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.sensing.data.GpsSample
import com.dhmeter.sensing.data.SensorBuffers
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Collects GPS location data using Fused Location Provider.
 * Includes GPS drift filtering to avoid false distance accumulation.
 */
@Singleton
class GpsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensitivityRepository: SensorSensitivityRepository
) {
    companion object {
        // GPS drift filtering thresholds
        private const val MIN_SPEED_MPS = 0.5f          // Minimum speed to accumulate distance
        private const val MAX_ACCURACY_M = 20f          // Maximum accuracy to consider valid
        private const val MIN_DISTANCE_FACTOR = 0.5f    // Movement must be > accuracy * factor
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private var buffers: SensorBuffers? = null
    private var sampleCount = 0
    private var totalDistance = 0.0
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastAccuracy: Float = Float.MAX_VALUE
    private var avgAccuracy = 0f
    private var accuracyCount = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Map wall-clock time to monotonic time
                val timestampNs = SystemClock.elapsedRealtimeNanos()

                // Calculate distance from last point
                val segmentDistance = lastLat?.let { prevLat ->
                    lastLon?.let { prevLon ->
                        haversineDistance(
                            prevLat, prevLon,
                            location.latitude, location.longitude
                        )
                    }
                } ?: 0.0

                // GPS drift filtering - only accumulate distance if:
                // 1. Current accuracy is acceptable
                // 2. Speed is above minimum threshold
                // 3. Movement distance is significantly larger than accuracy uncertainty
                val gpsSensitivity = sensitivityRepository.currentSettings.gpsSensitivity
                val maxAccuracyM = (MAX_ACCURACY_M / gpsSensitivity.coerceAtLeast(0.01f))
                    .coerceIn(10f, 60f)
                val isValidMovement = location.accuracy <= maxAccuracyM &&
                        location.speed >= MIN_SPEED_MPS &&
                        segmentDistance > (maxOf(location.accuracy, lastAccuracy) * MIN_DISTANCE_FACTOR)

                if (isValidMovement && segmentDistance > 0) {
                    totalDistance += segmentDistance
                }

                lastLat = location.latitude
                lastLon = location.longitude
                lastAccuracy = location.accuracy

                buffers?.gps?.add(
                    GpsSample(
                        timestampNs = timestampNs,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = location.speed,
                        accuracy = location.accuracy
                    )
                )

                avgAccuracy = ((avgAccuracy * accuracyCount) + location.accuracy) / (accuracyCount + 1)
                accuracyCount++
                sampleCount++
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(buffers: SensorBuffers): Boolean {
        this.buffers = buffers
        sampleCount = 0
        totalDistance = 0.0
        lastLat = null
        lastLon = null
        lastAccuracy = Float.MAX_VALUE
        avgAccuracy = 0f
        accuracyCount = 0

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000 // 1 second interval
        ).apply {
            setMinUpdateIntervalMillis(500)
            setMaxUpdateDelayMillis(2000)
        }.build()

        return try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop(): GpsCollectorStats {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        return GpsCollectorStats(
            sampleCount = sampleCount,
            totalDistance = totalDistance,
            avgAccuracy = avgAccuracy
        )
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula.
     */
    private fun haversineDistance(
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

data class GpsCollectorStats(
    val sampleCount: Int,
    val totalDistance: Double,
    val avgAccuracy: Float
)
