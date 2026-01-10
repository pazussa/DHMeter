package com.dhmeter.domain.model

/**
 * Configuration for a recording session.
 */
data class RunConfig(
    val trackId: String,
    val trackName: String,
    val placement: String = "POCKET_THIGH",
    val windowSec: Float = 1.0f,
    val hopSec: Float = 0.25f
)

/**
 * Handle to raw captured data, used for processing.
 */
data class RawCaptureHandle(
    val sessionId: String,
    val trackId: String,
    val startTimeNs: Long,
    val endTimeNs: Long,
    val deviceModel: String,
    val accelSampleRate: Float,
    val gyroSampleRate: Float,
    val baroSampleRate: Float?,
    val hasBarometer: Boolean,
    val gpsPointCount: Int,
    val accelSampleCount: Int,
    val gyroSampleCount: Int,
    val baroSampleCount: Int
)

/**
 * Result of processing a raw capture.
 */
data class ProcessedRun(
    val run: Run,
    val series: List<RunSeries>,
    val events: List<RunEvent>,
    val gpsPolyline: GpsPolyline? = null
)
