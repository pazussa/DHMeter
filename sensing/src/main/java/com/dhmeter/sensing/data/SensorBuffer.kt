package com.dhmeter.sensing.data

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe ring buffer for sensor samples.
 */
class SensorBuffer<T>(private val capacity: Int) {
    private val buffer = ArrayList<T>(capacity)
    private val lock = ReentrantLock()
    private var writeIndex = 0
    private var isFull = false

    fun add(sample: T) {
        lock.withLock {
            if (buffer.size < capacity) {
                buffer.add(sample)
            } else {
                buffer[writeIndex] = sample
            }
            writeIndex = (writeIndex + 1) % capacity
            if (writeIndex == 0 && buffer.size == capacity) {
                isFull = true
            }
        }
    }

    fun getAll(): List<T> {
        lock.withLock {
            return if (buffer.size < capacity) {
                buffer.toList()
            } else {
                // Return in chronological order
                val result = ArrayList<T>(capacity)
                for (i in writeIndex until capacity) {
                    result.add(buffer[i])
                }
                for (i in 0 until writeIndex) {
                    result.add(buffer[i])
                }
                result
            }
        }
    }

    fun clear() {
        lock.withLock {
            buffer.clear()
            writeIndex = 0
            isFull = false
        }
    }

    val size: Int
        get() = lock.withLock { buffer.size }
}

/**
 * Holds all sensor buffers for a recording session.
 * Uses LINEAR_ACCELERATION + GYROSCOPE + ROTATION_VECTOR + GPS.
 */
class SensorBuffers(
    accelCapacity: Int = 60000,    // ~5 min at 200Hz
    gyroCapacity: Int = 60000,
    rotationCapacity: Int = 60000, // Rotation vector for orientation
    gpsCapacity: Int = 600         // ~10 min at 1Hz
) {
    val accel = SensorBuffer<AccelSample>(accelCapacity)
    val gyro = SensorBuffer<GyroSample>(gyroCapacity)
    val rotation = SensorBuffer<RotationSample>(rotationCapacity)
    val gps = SensorBuffer<GpsSample>(gpsCapacity)

    fun clear() {
        accel.clear()
        gyro.clear()
        rotation.clear()
        gps.clear()
    }
}
