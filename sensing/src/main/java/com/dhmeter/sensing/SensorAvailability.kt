package com.dhmeter.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks availability of required sensors on the device.
 */
@Singleton
class SensorAvailability @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun hasAccelerometer(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    fun hasGyroscope(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    fun hasBarometer(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    fun hasRotationVector(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    fun hasGps(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun checkRequiredSensors(): SensorCheckResult {
        val missing = mutableListOf<String>()
        
        if (!hasAccelerometer()) missing.add("Accelerometer")
        if (!hasGyroscope()) missing.add("Gyroscope")
        if (!hasGps()) missing.add("GPS")
        
        return if (missing.isEmpty()) {
            SensorCheckResult.Success(hasBarometer = hasBarometer())
        } else {
            SensorCheckResult.MissingSensors(missing)
        }
    }
}

sealed class SensorCheckResult {
    data class Success(val hasBarometer: Boolean) : SensorCheckResult()
    data class MissingSensors(val missing: List<String>) : SensorCheckResult()
}
