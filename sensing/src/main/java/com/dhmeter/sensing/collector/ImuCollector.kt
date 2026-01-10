package com.dhmeter.sensing.collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.sensing.data.GyroSample
import com.dhmeter.sensing.data.RotationSample
import com.dhmeter.sensing.data.SensorBuffers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects data from IMU sensors (linear acceleration, gyroscope, rotation vector).
 * Uses TYPE_LINEAR_ACCELERATION which already has gravity removed - perfect for impact/vibration detection.
 * Uses TYPE_ROTATION_VECTOR for robust orientation alignment.
 */
@Singleton
class ImuCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var buffers: SensorBuffers? = null
    private var startTimeNs: Long = 0

    private var accelSampleCount = 0
    private var gyroSampleCount = 0
    private var rotationSampleCount = 0

    private val linearAccelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            buffers?.accel?.add(
                AccelSample(
                    timestampNs = event.timestamp,
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2]
                )
            )
            accelSampleCount++
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            buffers?.gyro?.add(
                GyroSample(
                    timestampNs = event.timestamp,
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2]
                )
            )
            gyroSampleCount++
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val rotationListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            buffers?.rotation?.add(
                RotationSample(
                    timestampNs = event.timestamp,
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    w = if (event.values.size > 3) event.values[3] else 0f,
                    accuracy = if (event.values.size > 4) event.values[4] else -1f
                )
            )
            rotationSampleCount++
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start(buffers: SensorBuffers): Boolean {
        this.buffers = buffers
        accelSampleCount = 0
        gyroSampleCount = 0
        rotationSampleCount = 0

        // Create dedicated handler thread for sensor callbacks
        handlerThread = HandlerThread("SensorThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        startTimeNs = System.nanoTime()

        // Register LINEAR_ACCELERATION (gravity already removed - ideal for impacts/vibration)
        val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAccel == null) return false
        sensorManager.registerListener(
            linearAccelListener,
            linearAccel,
            SensorManager.SENSOR_DELAY_FASTEST,
            handler
        )

        // Register gyroscope
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro == null) {
            stop()
            return false
        }
        sensorManager.registerListener(
            gyroListener,
            gyro,
            SensorManager.SENSOR_DELAY_FASTEST,
            handler
        )

        // Register ROTATION_VECTOR (for robust orientation alignment)
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            sensorManager.registerListener(
                rotationListener,
                rotationVector,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler
            )
        }
        // Note: Rotation vector is optional - we can fall back to magnitude-based calculations

        return true
    }

    fun stop(): ImuCollectorStats {
        sensorManager.unregisterListener(linearAccelListener)
        sensorManager.unregisterListener(gyroListener)
        sensorManager.unregisterListener(rotationListener)

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        val elapsedSec = (System.nanoTime() - startTimeNs) / 1e9f

        return ImuCollectorStats(
            accelSampleRate = if (elapsedSec > 0) accelSampleCount / elapsedSec else 0f,
            gyroSampleRate = if (elapsedSec > 0) gyroSampleCount / elapsedSec else 0f,
            rotationSampleRate = if (elapsedSec > 0) rotationSampleCount / elapsedSec else 0f,
            accelSampleCount = accelSampleCount,
            gyroSampleCount = gyroSampleCount,
            rotationSampleCount = rotationSampleCount
        )
    }
}

data class ImuCollectorStats(
    val accelSampleRate: Float,
    val gyroSampleRate: Float,
    val rotationSampleRate: Float = 0f,
    val accelSampleCount: Int,
    val gyroSampleCount: Int,
    val rotationSampleCount: Int = 0
)
