package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.RunRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

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

        if (values.isEmpty()) {
            return polyline.points.zipWithNext { start, end ->
                MapSegment(
                    start = start,
                    end = end,
                    distPct = (start.distPct + end.distPct) / 2f,
                    severity = SegmentSeverity.MEDIUM
                )
            }
        }
        
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

/**
 * Builds section split deltas for an individual run against the fastest run on the same track.
 */
class GetRunSectionComparisonUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    companion object {
        private val KEY_SECTION_BOUNDS = listOf(0f, 20f, 40f, 60f, 80f, 100f)
    }

    suspend operator fun invoke(runId: String): Result<RunMapSectionComparison?> {
        return try {
            val currentRun = runRepository.getRunById(runId) ?: return Result.success(null)
            val runsOnTrack = runRepository.getRunsByTrack(currentRun.trackId).first()
            if (runsOnTrack.isEmpty()) return Result.success(null)

            val fastestRun = runsOnTrack.minByOrNull { it.durationMs } ?: return Result.success(null)
            val currentProfile = buildTimingProfile(currentRun)
            val fastestProfile = if (fastestRun.runId == currentRun.runId) {
                currentProfile
            } else {
                buildTimingProfile(fastestRun)
            }

            val hasMeasuredSplitTiming = currentProfile.timingSeries != null &&
                    fastestProfile.timingSeries != null

            val sections = KEY_SECTION_BOUNDS.zipWithNext().mapIndexed { index, (startPct, endPct) ->
                val currentSection = calculateSectionTimeMs(currentProfile, startPct, endPct)
                val fastestSection = calculateSectionTimeMs(fastestProfile, startPct, endPct)
                val delta = if (currentSection == null || fastestSection == null) {
                    null
                } else {
                    currentSection - fastestSection
                }
                val currentAvgSpeed = calculateSectionAvgSpeed(
                    run = currentRun,
                    sectionMs = currentSection,
                    startPct = startPct,
                    endPct = endPct
                )
                val fastestAvgSpeed = calculateSectionAvgSpeed(
                    run = fastestRun,
                    sectionMs = fastestSection,
                    startPct = startPct,
                    endPct = endPct
                )

                RunMapSectionDelta(
                    sectionIndex = index + 1,
                    startDistPct = startPct,
                    endDistPct = endPct,
                    currentSectionMs = currentSection,
                    fastestSectionMs = fastestSection,
                    deltaVsFastestMs = delta,
                    currentAvgSpeedMps = currentAvgSpeed,
                    fastestAvgSpeedMps = fastestAvgSpeed
                )
            }

            Result.success(
                RunMapSectionComparison(
                    currentRunId = currentRun.runId,
                    fastestRunId = fastestRun.runId,
                    currentDurationMs = currentRun.durationMs,
                    fastestDurationMs = fastestRun.durationMs,
                    sections = sections,
                    hasMeasuredSplitTiming = hasMeasuredSplitTiming
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun buildTimingProfile(run: Run): RunTimingProfile {
        val timingSeries = runRepository.getSeries(run.runId, SeriesType.SPEED_TIME)
            ?.takeIf { it.seriesType == SeriesType.SPEED_TIME && it.pointCount > 0 }
        return RunTimingProfile(run, timingSeries)
    }

    private fun calculateSectionTimeMs(
        profile: RunTimingProfile,
        startDistPct: Float,
        endDistPct: Float
    ): Long? {
        val startMs = profile.elapsedMsAtDist(startDistPct) ?: return null
        val endMs = profile.elapsedMsAtDist(endDistPct) ?: return null
        return (endMs - startMs).coerceAtLeast(0L)
    }

    private fun calculateSectionAvgSpeed(
        run: Run,
        sectionMs: Long?,
        startPct: Float,
        endPct: Float
    ): Float? {
        val totalDistanceM = run.distanceMeters?.takeIf { it.isFinite() && it > 0f } ?: return null
        val durationMs = sectionMs?.takeIf { it > 0L } ?: return null
        val sectionDistanceM = totalDistanceM * ((endPct - startPct) / 100f)
        if (sectionDistanceM <= 0f) return null
        val speedMps = sectionDistanceM / (durationMs / 1000f)
        return speedMps.takeIf { it.isFinite() }
    }

    private data class RunTimingProfile(
        val run: Run,
        val timingSeries: RunSeries?
    ) {
        fun elapsedMsAtDist(distPct: Float): Long? {
            val clampedPct = distPct.coerceIn(0f, 100f)
            timingSeries?.let { series ->
                val valueSec = interpolateSeriesValue(series, clampedPct) ?: return null
                if (valueSec.isFinite()) {
                    return (valueSec * 1000f).roundToLong().coerceAtLeast(0L)
                }
            }

            val durationMs = run.durationMs
            if (durationMs <= 0L) return null
            return (durationMs * (clampedPct / 100f)).roundToLong()
        }

        private fun interpolateSeriesValue(series: RunSeries, targetX: Float): Float? {
            if (series.pointCount <= 0) return null

            var lowX = series.points[0]
            var lowY = series.points[1]
            if (targetX <= lowX) return lowY

            for (i in 1 until series.pointCount) {
                val hiX = series.points[i * 2]
                val hiY = series.points[i * 2 + 1]
                if (targetX <= hiX) {
                    val dx = hiX - lowX
                    return if (abs(dx) < 1e-6f) {
                        hiY
                    } else {
                        val t = (targetX - lowX) / dx
                        lowY + t * (hiY - lowY)
                    }
                }
                lowX = hiX
                lowY = hiY
            }

            return lowY
        }
    }
}
