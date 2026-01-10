package com.dhmeter.domain.model

/**
 * Represents a detected event during a run (landing, impact peak, harshness burst).
 */
data class RunEvent(
    val eventId: String,
    val runId: String,
    val type: String, // LANDING, IMPACT_PEAK, HARSHNESS_BURST
    val distPct: Float, // 0-100 position on track
    val timeSec: Float, // Time since run start
    val severity: Float, // Severity score
    val meta: EventMeta? = null
)

/**
 * Additional metadata for events, varies by type.
 */
data class EventMeta(
    val peakG: Float? = null,           // Peak acceleration in g
    val energy300ms: Float? = null,     // Energy in 300ms window post-peak
    val recoveryMs: Int? = null,        // Time to return to baseline
    val rmsValue: Float? = null,        // RMS value for harshness events
    val durationMs: Int? = null         // Duration of the event
)

object EventType {
    const val LANDING = "LANDING"
    const val IMPACT_PEAK = "IMPACT_PEAK"
    const val HARSHNESS_BURST = "HARSHNESS_BURST"
}
