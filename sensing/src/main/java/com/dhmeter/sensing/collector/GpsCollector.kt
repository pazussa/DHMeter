package com.dhmeter.sensing.collector

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.sensing.data.GpsSample
import com.dhmeter.sensing.data.SensorBuffers
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Collects GPS location data.
 * Uses Fused Location Provider when Google Play Services are available,
 * and falls back to platform LocationManager on devices without GMS.
 * Includes GPS drift filtering to avoid false distance accumulation.
 */
@Singleton
class GpsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensitivityRepository: SensorSensitivityRepository
) {
    companion object {
        private const val TAG = "GpsCollector"

        // GPS drift filtering thresholds
        private const val MIN_SPEED_MPS = 0.5f          // Minimum speed to accumulate distance
        private const val MAX_ACCURACY_M = 20f          // Maximum accuracy to consider valid
        private const val MIN_DISTANCE_FACTOR = 0.5f    // Movement must be > accuracy * factor
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private var usingFusedProvider = false

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
            result.lastLocation?.let(::handleLocation)
        }
    }

    private val platformLocationListener = LocationListener { location ->
        handleLocation(location)
    }

    private fun handleLocation(location: Location) {
        // Map wall-clock time to monotonic time
        val timestampNs = SystemClock.elapsedRealtimeNanos()

        // Calculate distance from last point
        val segmentDistance = lastLat?.let { prevLat ->
            lastLon?.let { prevLon ->
                haversineDistance(
                    prevLat,
                    prevLon,
                    location.latitude,
                    location.longitude
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
            segmentDistance > (max(location.accuracy, lastAccuracy) * MIN_DISTANCE_FACTOR)

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

        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission missing, cannot start GPS collector")
            return false
        }

        val fusedStarted = startFusedUpdates()
        if (fusedStarted) {
            usingFusedProvider = true
            return true
        }

        val platformStarted = startPlatformUpdates()
        usingFusedProvider = false

        if (!platformStarted) {
            Log.w(TAG, "No location provider available (Fused and LocationManager both unavailable)")
        }

        return platformStarted
    }

    @SuppressLint("MissingPermission")
    private fun startFusedUpdates(): Boolean {
        if (!hasLocationPermission()) {
            return false
        }

        if (!isGooglePlayServicesAvailable()) {
            Log.i(TAG, "Google Play Services unavailable, using LocationManager fallback")
            return false
        }

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
            Log.w(TAG, "Failed to start FusedLocationProvider, falling back to LocationManager", e)
            false
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformUpdates(): Boolean {
        val looper = Looper.getMainLooper()

        var started = false

        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        val networkEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

        if (gpsEnabled) {
            started = runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    platformLocationListener,
                    looper
                )
                true
            }.getOrElse {
                Log.w(TAG, "Failed to register GPS provider updates", it)
                false
            } || started
        }

        if (networkEnabled) {
            started = runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1500L,
                    0f,
                    platformLocationListener,
                    looper
                )
                true
            }.getOrElse {
                Log.w(TAG, "Failed to register network provider updates", it)
                false
            } || started
        }

        return started
    }

    fun stop(): GpsCollectorStats {
        if (usingFusedProvider) {
            runCatching {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }.onFailure {
                Log.w(TAG, "Failed to remove fused location updates", it)
            }
        }

        runCatching {
            locationManager.removeUpdates(platformLocationListener)
        }.onFailure {
            Log.w(TAG, "Failed to remove platform location updates", it)
        }

        usingFusedProvider = false

        return GpsCollectorStats(
            sampleCount = sampleCount,
            totalDistance = totalDistance,
            avgAccuracy = avgAccuracy
        )
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrElse {
            Log.w(TAG, "Unable to verify Google Play Services availability", it)
            false
        }
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula.
     */
    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

data class GpsCollectorStats(
    val sampleCount: Int,
    val totalDistance: Double,
    val avgAccuracy: Float
)
