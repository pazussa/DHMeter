package com.dhmeter.domain.model

data class SensorSensitivitySettings(
    val impactSensitivity: Float = DEFAULT_IMPACT_SENSITIVITY,
    val harshnessSensitivity: Float = DEFAULT_HARSHNESS_SENSITIVITY,
    val stabilitySensitivity: Float = DEFAULT_STABILITY_SENSITIVITY,
    val gpsSensitivity: Float = DEFAULT_GPS_SENSITIVITY
) {
    fun normalized(): SensorSensitivitySettings = copy(
        impactSensitivity = impactSensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
        harshnessSensitivity = harshnessSensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
        stabilitySensitivity = stabilitySensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
        gpsSensitivity = gpsSensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
    )

    companion object {
        const val MIN_SENSITIVITY = 0.1f
        const val MAX_SENSITIVITY = 5.0f
        const val DEFAULT_SENSITIVITY = 1.0f
        const val DEFAULT_IMPACT_SENSITIVITY = 0.62f
        const val DEFAULT_HARSHNESS_SENSITIVITY = 5.0f
        const val DEFAULT_STABILITY_SENSITIVITY = 0.1f
        const val DEFAULT_GPS_SENSITIVITY = 1.0f
    }
}
