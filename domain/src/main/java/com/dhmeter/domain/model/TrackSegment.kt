package com.dhmeter.domain.model

/**
 * Local segment derived from a previously recorded run.
 */
data class TrackSegment(
    val id: String,
    val trackId: String,
    val sourceRunId: String,
    val startedAt: Long,
    val start: SegmentPoint,
    val end: SegmentPoint
)

data class SegmentPoint(
    val lat: Double,
    val lon: Double
)
