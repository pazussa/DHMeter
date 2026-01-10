package com.dhmeter.sensing.data

/**
 * Raw accelerometer sample with timestamp.
 */
data class AccelSample(
    val timestampNs: Long,
    val x: Float,
    val y: Float,
    val z: Float
) {
    val magnitude: Float
        get() = kotlin.math.sqrt(x * x + y * y + z * z)
}

/**
 * Raw gyroscope sample with timestamp.
 */
data class GyroSample(
    val timestampNs: Long,
    val x: Float, // rad/s around X axis
    val y: Float, // rad/s around Y axis
    val z: Float  // rad/s around Z axis
) {
    val magnitude: Float
        get() = kotlin.math.sqrt(x * x + y * y + z * z)
}

/**
 * Rotation vector sample (quaternion representation).
 * From TYPE_ROTATION_VECTOR sensor for robust orientation.
 */
data class RotationSample(
    val timestampNs: Long,
    val x: Float, // Rotation vector component x (x * sin(θ/2))
    val y: Float, // Rotation vector component y (y * sin(θ/2))
    val z: Float, // Rotation vector component z (z * sin(θ/2))
    val w: Float, // Scalar component (cos(θ/2)), optional
    val accuracy: Float // Estimated heading accuracy in radians, -1 if unavailable
)

/**
 * GPS location sample.
 */
data class GpsSample(
    val timestampNs: Long, // Mapped to monotonic time
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float, // m/s
    val accuracy: Float // meters
)
