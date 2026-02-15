package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.RunRepository
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Compares multiple runs on the same track and generates a multi-run comparison result.
 */
class CompareMultipleRunsUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    companion object {
        private const val IMPACT_REF = 25f
        private const val HARSHNESS_REF = 1.2f
        private const val STABILITY_REF = 0.35f
        private val KEY_SECTION_BOUNDS = listOf(0f, 20f, 40f, 60f, 80f, 100f)
    }

    // Color palette for runs (stored as ARGB Long values)
    private val runColors = listOf(
        0xFF2196F3, // Blue
        0xFFFF5722, // Orange
        0xFF4CAF50, // Green
        0xFF9C27B0, // Purple
        0xFFFFEB3B, // Yellow
        0xFF00BCD4, // Cyan
        0xFFE91E63, // Pink
        0xFF795548  // Brown
    )

    suspend operator fun invoke(
        trackId: String,
        runIds: List<String>
    ): Result<MultiRunComparisonResult> {
        return try {
            if (runIds.size < 2) {
                return Result.failure(Exception("Need at least 2 runs to compare"))
            }

            // Load all runs
            val runs = runIds.mapIndexedNotNull { index, runId ->
                runRepository.getRunById(runId)?.let { run ->
                    RunWithColor(
                        run = run,
                        color = runColors[index % runColors.size],
                        label = "Run ${index + 1}"
                    )
                }
            }

            if (runs.size < 2) {
                return Result.failure(Exception("Could not load enough runs"))
            }

            // Verify all runs are from same track
            if (runs.any { it.run.trackId != trackId }) {
                return Result.failure(Exception("All runs must be from the same track"))
            }

            // Build metric comparisons
            val metricComparisons = buildMetricComparisons(runs)

            // Build map comparison (route overlay + split deltas vs Run 1)
            val mapComparison = buildMapComparison(runs)
            val altitudeComparison = buildAltitudeComparison(runs)

            // Determine verdict
            val verdict = determineVerdict(runs, metricComparisons)

            // Generate insights
            val insights = (generateInsights(metricComparisons) + generateSectionDeltaInsights(mapComparison))
                .distinct()
                .take(6)

            Result.success(
                MultiRunComparisonResult(
                    trackId = trackId,
                    runs = runs,
                    metricComparisons = metricComparisons,
                    verdict = verdict,
                    sectionInsights = insights,
                    mapComparison = mapComparison,
                    altitudeComparison = altitudeComparison
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildMetricComparisons(runs: List<RunWithColor>): List<MultiMetricComparison> {
        val impactScores = runs.map { it.run.impactScore?.let { value -> normalizeBurden(SeriesType.IMPACT_DENSITY, value) } }
        val harshnessScores = runs.map { it.run.harshnessAvg?.let { value -> normalizeBurden(SeriesType.HARSHNESS, value) } }
        val stabilityScores = runs.map { it.run.stabilityScore?.let { value -> normalizeBurden(SeriesType.STABILITY, value) } }
        val maxSpeeds = runs.map { it.run.maxSpeed?.let { value -> value * 3.6f } }

        return listOf(
            MultiMetricComparison(
                metricName = "Impact",
                values = impactScores,
                bestRunIndex = findBestRunIndex(impactScores, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Harshness",
                values = harshnessScores,
                bestRunIndex = findBestRunIndex(harshnessScores, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Stability",
                values = stabilityScores,
                bestRunIndex = findBestRunIndex(stabilityScores, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Landing Quality",
                values = runs.map { it.run.landingQualityScore },
                bestRunIndex = findBestRunIndex(runs.map { it.run.landingQualityScore }, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Duration",
                values = runs.map { it.run.durationMs.toFloat() / 1000f },
                bestRunIndex = findBestRunIndex(runs.map { it.run.durationMs.toFloat() / 1000f }, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Max Speed",
                values = maxSpeeds,
                bestRunIndex = findBestRunIndex(maxSpeeds, lowerIsBetter = false),
                lowerIsBetter = false
            )
        )
    }

    private fun normalizeBurden(type: SeriesType, value: Float): Float {
        val nonNegativeValue = value.coerceAtLeast(0f)
        val normalized = when (type) {
            SeriesType.IMPACT_DENSITY -> (nonNegativeValue / (nonNegativeValue + IMPACT_REF)) * 100f
            SeriesType.HARSHNESS -> (nonNegativeValue / (nonNegativeValue + HARSHNESS_REF)) * 100f
            SeriesType.STABILITY -> (nonNegativeValue / (nonNegativeValue + STABILITY_REF)) * 100f
            SeriesType.SPEED_TIME -> value
        }
        return normalized.coerceIn(0f, 100f)
    }

    private fun findBestRunIndex(values: List<Float?>, lowerIsBetter: Boolean): Int? {
        val validIndices = values.mapIndexedNotNull { index, value ->
            if (value != null && value.isFinite()) index to value else null
        }
        if (validIndices.isEmpty()) return null
        
        return if (lowerIsBetter) {
            validIndices.minByOrNull { it.second }?.first
        } else {
            validIndices.maxByOrNull { it.second }?.first
        }
    }

    private fun determineVerdict(
        runs: List<RunWithColor>,
        comparisons: List<MultiMetricComparison>
    ): MultiRunVerdict {
        // Count how many times each run is "best"
        val bestCounts = IntArray(runs.size)
        comparisons.forEach { comparison ->
            comparison.bestRunIndex?.let { bestCounts[it]++ }
        }

        val maxWins = bestCounts.maxOrNull() ?: 0
        val winnersCount = bestCounts.count { it == maxWins }

        return when {
            // Clear winner (one run has most wins)
            maxWins >= 3 && winnersCount == 1 -> {
                val bestIndex = bestCounts.indexOfFirst { it == maxWins }
                val bestRun = runs[bestIndex]
                val highlights = buildWinnerHighlights(bestIndex, comparisons)
                MultiRunVerdict.createClearWinner(bestIndex, bestRun.label, highlights)
            }
            
            // Mixed results (multiple runs excel at different things)
            maxWins > 0 -> {
                val highlights = buildMixedHighlights(runs, comparisons)
                MultiRunVerdict.createMixed(highlights)
            }
            
            // Similar (no clear best)
            else -> MultiRunVerdict.createSimilar()
        }
    }

    private fun buildWinnerHighlights(bestIndex: Int, comparisons: List<MultiMetricComparison>): String {
        val wonMetrics = comparisons.filter { it.bestRunIndex == bestIndex }
        if (wonMetrics.isEmpty()) return "Most metrics are similar across runs."
        return wonMetrics.joinToString(". ") { "Best ${it.metricName.cleanMetricName().lowercase()}" } + "."
    }

    private fun buildMixedHighlights(runs: List<RunWithColor>, comparisons: List<MultiMetricComparison>): String {
        val highlights = mutableListOf<String>()
        comparisons.forEach { comparison ->
            comparison.bestRunIndex?.let { bestIdx ->
                highlights.add("${runs[bestIdx].label} has best ${comparison.metricName.cleanMetricName().lowercase()}")
            }
        }
        if (highlights.isEmpty()) return "Metrics are too close to declare a clear advantage."
        return highlights.take(3).joinToString(". ") + "."
    }

    private fun generateInsights(comparisons: List<MultiMetricComparison>): List<String> {
        val insights = mutableListOf<String>()

        // Find biggest differences
        comparisons.forEach { comparison ->
            val validValues = comparison.values.filterNotNull().filter { it.isFinite() }
            if (validValues.size >= 2) {
                val min = validValues.minOrNull() ?: 0f
                val max = validValues.maxOrNull() ?: 0f
                if (min > 0f) {
                    val range = ((max - min) / min) * 100
                    if (range > 30) {
                        insights.add("${comparison.metricName.cleanMetricName()} varies by ${range.toInt()}% across runs")
                    }
                } else if (max - min >= 10f) {
                    insights.add("${comparison.metricName.cleanMetricName()} spread is ${String.format("%.0f", max - min)} points")
                }
            }
        }
        return insights.take(5)
    }

    private suspend fun buildMapComparison(runs: List<RunWithColor>): MapComparisonData? {
        val mapRuns = runs.mapNotNull { runWithColor ->
            val polyline = runRepository.getGpsPolyline(runWithColor.run.runId) ?: return@mapNotNull null
            if (polyline.points.size < 2) return@mapNotNull null
            MapComparisonRun(
                runId = runWithColor.run.runId,
                runLabel = runWithColor.label,
                color = runWithColor.color,
                polyline = polyline
            )
        }
        if (mapRuns.size < 2) return null

        val runById = runs.associateBy { it.run.runId }
        val timingProfiles = mapRuns.map { mapRun ->
            val baseRun = runById[mapRun.runId]?.run ?: return null
            val timingSeries = runRepository.getSeries(mapRun.runId, SeriesType.SPEED_TIME)
            RunTimingProfile(baseRun, timingSeries)
        }
        val hasMeasuredSplitTiming = timingProfiles.all {
            val series = it.timingSeries
            series != null && series.effectivePointCount > 0
        }

        val sections = KEY_SECTION_BOUNDS.zipWithNext().mapIndexed { index, (startPct, endPct) ->
            val sectionTimes = timingProfiles.map { profile ->
                calculateSectionTimeMs(profile, startPct, endPct)
            }
            val sectionSpeeds = timingProfiles.mapIndexed { profileIndex, profile ->
                calculateSectionAvgSpeed(profile.run, sectionTimes.getOrNull(profileIndex), startPct, endPct)
            }

            val baseline = sectionTimes.firstOrNull()
            val deltas = sectionTimes.mapIndexed { runIndex, timeMs ->
                when {
                    timeMs == null || baseline == null -> null
                    runIndex == 0 -> 0L
                    else -> timeMs - baseline
                }
            }

            MapSectionDelta(
                sectionIndex = index + 1,
                startDistPct = startPct,
                endDistPct = endPct,
                sectionTimesMs = sectionTimes,
                deltaVsBaselineMs = deltas,
                sectionAvgSpeedMps = sectionSpeeds,
                bestRunIndex = findBestSectionIndex(sectionTimes)
            )
        }

        val baselineDuration = timingProfiles.first().run.durationMs
        val totalDeltas = timingProfiles.mapIndexed { index, profile ->
            if (index == 0) 0L else profile.run.durationMs - baselineDuration
        }

        return MapComparisonData(
            baselineRunIndex = 0,
            runs = mapRuns,
            sections = sections,
            totalDeltaVsBaselineMs = totalDeltas,
            hasMeasuredSplitTiming = hasMeasuredSplitTiming
        )
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

    private fun findBestSectionIndex(sectionTimes: List<Long?>): Int? {
        return sectionTimes
            .mapIndexedNotNull { index, value -> value?.let { index to it } }
            .minByOrNull { it.second }
            ?.first
    }

    private fun generateSectionDeltaInsights(mapComparison: MapComparisonData?): List<String> {
        mapComparison ?: return emptyList()
        if (mapComparison.runs.size < 2 || mapComparison.sections.isEmpty()) return emptyList()

        val challengerIndex = 1
        val challengerLabel = mapComparison.runs[challengerIndex].runLabel
        val deltas = mapComparison.sections.mapNotNull { section ->
            section.deltaVsBaselineMs.getOrNull(challengerIndex)?.let { delta ->
                section to delta
            }
        }
        if (deltas.isEmpty()) return emptyList()

        val biggestGain = deltas.minByOrNull { it.second }
        val biggestLoss = deltas.maxByOrNull { it.second }
        val insights = mutableListOf<String>()

        biggestGain?.takeIf { it.second < 0L }?.let { (section, delta) ->
            insights.add(
                "$challengerLabel gained ${formatAbsDelta(delta)} in S${section.sectionIndex} (${section.startDistPct.toInt()}-${section.endDistPct.toInt()}%)"
            )
        }
        biggestLoss?.takeIf { it.second > 0L }?.let { (section, delta) ->
            insights.add(
                "$challengerLabel lost ${formatAbsDelta(delta)} in S${section.sectionIndex} (${section.startDistPct.toInt()}-${section.endDistPct.toInt()}%)"
            )
        }

        return insights
    }

    private suspend fun buildAltitudeComparison(runs: List<RunWithColor>): AltitudeComparisonData? {
        val altitudeRuns = runs.mapNotNull { runWithColor ->
            val polyline = runRepository.getGpsPolyline(runWithColor.run.runId) ?: return@mapNotNull null
            val profile = createSmoothedElevationProfile(polyline.points) ?: return@mapNotNull null
            val (descent, ascent) = calculateElevationTotals(profile)
            AltitudeComparisonRun(
                runId = runWithColor.run.runId,
                runLabel = runWithColor.label,
                color = runWithColor.color,
                profilePoints = profile,
                totalDescentM = descent,
                totalAscentM = ascent
            )
        }

        if (altitudeRuns.size < 2) return null

        val sections = KEY_SECTION_BOUNDS.zipWithNext().mapIndexed { index, (startPct, endPct) ->
            val sectionDeltas = altitudeRuns.map { run ->
                calculateSectionElevation(run.profilePoints, startPct, endPct)
            }
            AltitudeSectionDelta(
                sectionIndex = index + 1,
                startDistPct = startPct,
                endDistPct = endPct,
                descentMeters = sectionDeltas.map { it?.first },
                ascentMeters = sectionDeltas.map { it?.second }
            )
        }

        return AltitudeComparisonData(
            baselineRunIndex = 0,
            runs = altitudeRuns,
            sections = sections
        )
    }

    private fun createSmoothedElevationProfile(points: List<GpsPoint>): List<ElevationProfilePoint>? {
        val altitudePoints = points
            .filter { it.altitudeM != null && it.distPct.isFinite() }
            .sortedBy { it.distPct }
        if (altitudePoints.size < 2) return null

        val altitudes = altitudePoints.mapNotNull { it.altitudeM }
        val smoothed = smoothAltitude(altitudes)
        if (smoothed.size != altitudePoints.size) return null

        return altitudePoints.indices.map { index ->
            ElevationProfilePoint(
                distPct = altitudePoints[index].distPct.coerceIn(0f, 100f),
                altitudeM = smoothed[index]
            )
        }
    }

    private fun smoothAltitude(values: List<Float>): List<Float> {
        if (values.size < 3) return values
        val output = MutableList(values.size) { values[it] }
        for (i in values.indices) {
            val prev = values[(i - 1).coerceAtLeast(0)]
            val current = values[i]
            val next = values[(i + 1).coerceAtMost(values.lastIndex)]
            output[i] = (prev + current + next) / 3f
        }
        return output
    }

    private fun calculateElevationTotals(profile: List<ElevationProfilePoint>): Pair<Float, Float> {
        var descent = 0f
        var ascent = 0f
        profile.zipWithNext { a, b ->
            val delta = b.altitudeM - a.altitudeM
            if (delta < 0f) {
                descent += -delta
            } else {
                ascent += delta
            }
        }
        return descent to ascent
    }

    private fun calculateSectionElevation(
        points: List<ElevationProfilePoint>,
        startDistPct: Float,
        endDistPct: Float
    ): Pair<Float, Float>? {
        if (points.size < 2) return null
        val start = startDistPct.coerceIn(0f, 100f)
        val end = endDistPct.coerceIn(0f, 100f)
        if (end <= start) return null

        val startAltitude = interpolateAltitude(points, start) ?: return null
        val endAltitude = interpolateAltitude(points, end) ?: return null

        val sectionPoints = buildList {
            add(ElevationProfilePoint(start, startAltitude))
            points.filterTo(this) { it.distPct > start && it.distPct < end }
            add(ElevationProfilePoint(end, endAltitude))
        }.sortedBy { it.distPct }

        if (sectionPoints.size < 2) return null

        var descent = 0f
        var ascent = 0f
        sectionPoints.zipWithNext { a, b ->
            val delta = b.altitudeM - a.altitudeM
            if (delta < 0f) {
                descent += -delta
            } else {
                ascent += delta
            }
        }
        return descent to ascent
    }

    private fun interpolateAltitude(points: List<ElevationProfilePoint>, targetDistPct: Float): Float? {
        if (points.isEmpty()) return null
        if (targetDistPct <= points.first().distPct) return points.first().altitudeM
        if (targetDistPct >= points.last().distPct) return points.last().altitudeM

        var lower = points.first()
        for (i in 1 until points.size) {
            val upper = points[i]
            if (targetDistPct <= upper.distPct) {
                val span = (upper.distPct - lower.distPct)
                if (abs(span) < 1e-6f) return upper.altitudeM
                val t = (targetDistPct - lower.distPct) / span
                return lower.altitudeM + t * (upper.altitudeM - lower.altitudeM)
            }
            lower = upper
        }
        return points.last().altitudeM
    }

    private fun formatAbsDelta(deltaMs: Long): String {
        val absMs = abs(deltaMs)
        return if (absMs >= 1000L) {
            String.format("%.2f s", absMs / 1000f)
        } else {
            "$absMs ms"
        }
    }

    private fun String.cleanMetricName(): String = this

    private data class RunTimingProfile(
        val run: Run,
        val timingSeries: RunSeries?
    ) {
        fun elapsedMsAtDist(distPct: Float): Long? {
            val clampedPct = distPct.coerceIn(0f, 100f)
            timingSeries?.takeIf { it.seriesType == SeriesType.SPEED_TIME && it.effectivePointCount > 0 }?.let { series ->
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
            val pointCount = series.effectivePointCount
            if (pointCount <= 0) return null

            var lowX = series.points[0]
            var lowY = series.points[1]
            if (targetX <= lowX) return lowY

            for (i in 1 until pointCount) {
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

