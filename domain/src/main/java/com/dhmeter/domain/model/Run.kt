package com.dhmeter.domain.model

data class Run(
    val runId: String,
    val trackId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val phonePlacement: String = "POCKET_THIGH",
    val deviceModel: String,
    val sampleRateAccelHz: Float,
    val sampleRateGyroHz: Float,
    val sampleRateBaroHz: Float? = null,
    val gpsQuality: GpsQuality,
    
    // Distance and movement stats
    val distanceMeters: Float? = null,
    val pauseCount: Int = 0,
    
    // Summary metrics
    val impactScore: Float? = null,
    val harshnessAvg: Float? = null,
    val harshnessP90: Float? = null,
    val stabilityScore: Float? = null,
    val landingQualityScore: Float? = null,
    val avgSpeed: Float? = null,
    val slopeClassAvg: Int? = null, // 0=gentle, 1=medium, 2=steep
    
    // User tags
    val setupNote: String? = null,
    val conditionsNote: String? = null
)

enum class GpsQuality {
    EXCELLENT, GOOD, FAIR, MEDIUM, POOR
}

enum class InvalidRunReason(val description: String) {
    SIGNAL_LOOSE("Phone signal unstable (loose in pocket)"),
    TOO_SHORT("Run duration too short"),
    TOO_LONG("Run duration too long"),
    NO_MOVEMENT("No continuous movement detected"),
    GPS_POOR("GPS signal too poor for alignment"),
    POOR_SIGNAL("Sensor signal quality too poor"),
    SENSOR_ERROR("Sensor data error or missing"),
    MANUAL_DISCARD("Manually discarded by user"),
    LONG_PAUSES("Long pauses detected during run")
}
