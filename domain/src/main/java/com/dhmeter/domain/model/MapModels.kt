package com.dhmeter.domain.model

/**
 * A single GPS point for map visualization.
 */
data class GpsPoint(
    val lat: Double,
    val lon: Double,
    val distPct: Float // 0..100
)

/**
 * GPS polyline for rendering on map.
 */
data class GpsPolyline(
    val runId: String,
    val points: List<GpsPoint>,
    val totalDistanceM: Float,
    val avgAccuracyM: Float,
    val gpsQuality: MapGpsQuality
)

/**
 * GPS quality classification for map display.
 */
enum class MapGpsQuality(val description: String) {
    GOOD("Good GPS signal"),      // avg accuracy < 15m
    OK("Moderate GPS signal"),     // 15-30m
    POOR("Poor GPS signal")        // > 30m
}

/**
 * A segment on the map with severity coloring.
 */
data class MapSegment(
    val start: GpsPoint,
    val end: GpsPoint,
    val distPct: Float, // Midpoint distPct
    val severity: SegmentSeverity
)

/**
 * Severity level for map segment coloring.
 */
enum class SegmentSeverity {
    VERY_LOW,  // <= P20
    LOW,       // P20-P40
    MEDIUM,    // P40-P60
    HIGH,      // P60-P80
    VERY_HIGH  // > P80
}

/**
 * Metric type for coloring the map.
 */
enum class MapMetricType(val displayName: String) {
    IMPACT("Impact"),
    HARSHNESS("Harshness"),
    STABILITY("Stability"),
    SPEED("Speed")
}

/**
 * Event marker for the map.
 */
data class MapEventMarker(
    val eventId: String,
    val location: GpsPoint,
    val type: MapEventType,
    val severity: Float,
    val distPct: Float,
    val meta: Map<String, Any> // peak, energy300ms, recoveryMs
)

/**
 * Type of event marker.
 */
enum class MapEventType(val displayName: String) {
    IMPACT_PEAK("Strong Impact"),
    LANDING("Hard Landing")
}

/**
 * Complete map data for a run.
 */
data class RunMapData(
    val runId: String,
    val polyline: GpsPolyline,
    val segments: List<MapSegment>,
    val events: List<MapEventMarker>,
    val percentiles: MetricPercentiles,
    val activeMetric: MapMetricType
)

/**
 * Percentiles for a metric series, used for coloring.
 */
data class MetricPercentiles(
    val p20: Float,
    val p40: Float,
    val p60: Float,
    val p80: Float
)

/**
 * Split-time comparison for a single run against the fastest run of the same track.
 */
data class RunMapSectionComparison(
    val currentRunId: String,
    val fastestRunId: String,
    val currentDurationMs: Long,
    val fastestDurationMs: Long,
    val sections: List<RunMapSectionDelta>,
    val hasMeasuredSplitTiming: Boolean
)

data class RunMapSectionDelta(
    val sectionIndex: Int,
    val startDistPct: Float,
    val endDistPct: Float,
    val currentSectionMs: Long?,
    val fastestSectionMs: Long?,
    val deltaVsFastestMs: Long?,
    val currentAvgSpeedMps: Float?,
    val fastestAvgSpeedMps: Float?
)
