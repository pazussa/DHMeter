package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.RunRepository
import javax.inject.Inject

/**
 * Gets all data needed for map visualization of a run.
 */
class GetRunMapDataUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke(runId: String): Result<RunMapData?> {
        return try {
            val polyline = runRepository.getGpsPolyline(runId)
                ?: return Result.success(null)
            
            val events = runRepository.getEvents(runId)
            val impactSeries = runRepository.getSeries(runId, SeriesType.IMPACT_DENSITY)
            
            // Calculate percentiles for impact (default metric)
            val impactValues = impactSeries?.getYValues() ?: emptyList()
            val percentiles = calculatePercentiles(impactValues)
            
            // Create segments colored by impact
            val segments = createSegments(polyline, impactValues, percentiles)
            
            // Create event markers
            val eventMarkers = createEventMarkers(events, polyline)
            
            Result.success(
                RunMapData(
                    runId = runId,
                    polyline = polyline,
                    segments = segments,
                    events = eventMarkers,
                    percentiles = percentiles,
                    activeMetric = MapMetricType.IMPACT
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get map data with different metric coloring.
     */
    suspend fun withMetric(runId: String, metric: MapMetricType): Result<RunMapData?> {
        return try {
            val polyline = runRepository.getGpsPolyline(runId)
                ?: return Result.success(null)
            
            val events = runRepository.getEvents(runId)
            
            val seriesType = when (metric) {
                MapMetricType.IMPACT -> SeriesType.IMPACT_DENSITY
                MapMetricType.HARSHNESS -> SeriesType.HARSHNESS
                MapMetricType.STABILITY -> SeriesType.STABILITY
            }
            
            val series = runRepository.getSeries(runId, seriesType)
            val values = series?.getYValues() ?: emptyList()
            val percentiles = calculatePercentiles(values)
            val segments = createSegments(polyline, values, percentiles)
            val eventMarkers = createEventMarkers(events, polyline)
            
            Result.success(
                RunMapData(
                    runId = runId,
                    polyline = polyline,
                    segments = segments,
                    events = eventMarkers,
                    percentiles = percentiles,
                    activeMetric = metric
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun calculatePercentiles(values: List<Float>): MetricPercentiles {
        if (values.isEmpty()) return MetricPercentiles(0f, 0f, 0f, 0f)
        
        val sorted = values.sorted()
        val p20Index = (sorted.size * 0.2).toInt().coerceAtMost(sorted.lastIndex)
        val p40Index = (sorted.size * 0.4).toInt().coerceAtMost(sorted.lastIndex)
        val p60Index = (sorted.size * 0.6).toInt().coerceAtMost(sorted.lastIndex)
        val p80Index = (sorted.size * 0.8).toInt().coerceAtMost(sorted.lastIndex)
        
        return MetricPercentiles(
            p20 = sorted[p20Index],
            p40 = sorted[p40Index],
            p60 = sorted[p60Index],
            p80 = sorted[p80Index]
        )
    }
    
    private fun createSegments(
        polyline: GpsPolyline,
        values: List<Float>,
        percentiles: MetricPercentiles
    ): List<MapSegment> {
        if (polyline.points.size < 2) return emptyList()
        
        return polyline.points.zipWithNext { start, end ->
            val midPct = (start.distPct + end.distPct) / 2
            val valueIndex = (midPct / 100f * (values.size - 1)).toInt()
                .coerceIn(0, values.lastIndex.coerceAtLeast(0))
            
            val value = if (values.isNotEmpty()) values[valueIndex] else 0f
            
            val severity = when {
                value <= percentiles.p20 -> SegmentSeverity.VERY_LOW
                value <= percentiles.p40 -> SegmentSeverity.LOW
                value <= percentiles.p60 -> SegmentSeverity.MEDIUM
                value <= percentiles.p80 -> SegmentSeverity.HIGH
                else -> SegmentSeverity.VERY_HIGH
            }
            
            MapSegment(
                start = start,
                end = end,
                distPct = midPct,
                severity = severity
            )
        }
    }
    
    private fun createEventMarkers(
        events: List<RunEvent>,
        polyline: GpsPolyline
    ): List<MapEventMarker> {
        if (polyline.points.isEmpty()) return emptyList()
        
        return events.mapNotNull { event ->
            // Find closest GPS point to event's distPct
            val closestPoint = polyline.points.minByOrNull { 
                kotlin.math.abs(it.distPct - event.distPct) 
            } ?: return@mapNotNull null
            
            val eventType = when (event.type) {
                EventType.LANDING -> MapEventType.LANDING
                EventType.IMPACT_PEAK -> MapEventType.IMPACT_PEAK
                else -> return@mapNotNull null
            }
            
            MapEventMarker(
                eventId = event.eventId,
                location = closestPoint,
                type = eventType,
                severity = event.severity,
                distPct = event.distPct,
                meta = buildMap {
                    event.meta?.peakG?.let { put("peakG", it) }
                    event.meta?.energy300ms?.let { put("energy300ms", it) }
                    event.meta?.recoveryMs?.let { put("recoveryMs", it) }
                }
            )
        }
    }
}
