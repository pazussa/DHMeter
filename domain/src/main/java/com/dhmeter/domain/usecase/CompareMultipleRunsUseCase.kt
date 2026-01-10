package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.PreferencesRepository
import com.dhmeter.domain.repository.RunRepository
import javax.inject.Inject

/**
 * Compares multiple runs on the same track and generates a multi-run comparison result.
 */
class CompareMultipleRunsUseCase @Inject constructor(
    private val runRepository: RunRepository,
    private val preferencesRepository: PreferencesRepository
) {
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

            // Check validity
            val includeInvalid = preferencesRepository.getIncludeInvalidRuns()
            val invalidRuns = runs.filter { !it.run.isValid }
            if (!includeInvalid && invalidRuns.isNotEmpty()) {
                return Result.failure(Exception(
                    "Some runs are invalid: ${invalidRuns.map { it.label }.joinToString()}. " +
                    "Enable 'Include invalid runs' in settings to compare anyway."
                ))
            }

            // Build metric comparisons
            val metricComparisons = buildMetricComparisons(runs)

            // Determine verdict
            val verdict = determineVerdict(runs, metricComparisons)

            // Generate insights
            val insights = generateInsights(runs, metricComparisons)

            Result.success(
                MultiRunComparisonResult(
                    trackId = trackId,
                    runs = runs,
                    metricComparisons = metricComparisons,
                    verdict = verdict,
                    sectionInsights = insights
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildMetricComparisons(runs: List<RunWithColor>): List<MultiMetricComparison> {
        return listOf(
            MultiMetricComparison(
                metricName = "Impact Score",
                values = runs.map { it.run.impactScore },
                bestRunIndex = findBestRunIndex(runs.map { it.run.impactScore }, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Harshness",
                values = runs.map { it.run.harshnessAvg },
                bestRunIndex = findBestRunIndex(runs.map { it.run.harshnessAvg }, lowerIsBetter = true),
                lowerIsBetter = true
            ),
            MultiMetricComparison(
                metricName = "Stability",
                values = runs.map { it.run.stabilityScore },
                bestRunIndex = findBestRunIndex(runs.map { it.run.stabilityScore }, lowerIsBetter = true),
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
            )
        )
    }

    private fun findBestRunIndex(values: List<Float?>, lowerIsBetter: Boolean): Int? {
        val validIndices = values.mapIndexedNotNull { index, value ->
            if (value != null && value > 0) index to value else null
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
        return wonMetrics.joinToString(". ") { "Best ${it.metricName.lowercase()}" } + "."
    }

    private fun buildMixedHighlights(runs: List<RunWithColor>, comparisons: List<MultiMetricComparison>): String {
        val highlights = mutableListOf<String>()
        comparisons.forEach { comparison ->
            comparison.bestRunIndex?.let { bestIdx ->
                highlights.add("${runs[bestIdx].label} has best ${comparison.metricName.lowercase()}")
            }
        }
        return highlights.take(3).joinToString(". ") + "."
    }

    private fun generateInsights(
        runs: List<RunWithColor>,
        comparisons: List<MultiMetricComparison>
    ): List<String> {
        val insights = mutableListOf<String>()

        // Find biggest differences
        comparisons.forEach { comparison ->
            val validValues = comparison.values.filterNotNull().filter { it > 0 }
            if (validValues.size >= 2) {
                val min = validValues.minOrNull() ?: 0f
                val max = validValues.maxOrNull() ?: 0f
                if (min > 0) {
                    val range = ((max - min) / min) * 100
                    if (range > 30) {
                        insights.add("${comparison.metricName} varies by ${range.toInt()}% across runs")
                    }
                }
            }
        }

        // Note invalid runs
        val invalidRuns = runs.filter { !it.run.isValid }
        if (invalidRuns.isNotEmpty()) {
            insights.add("⚠️ ${invalidRuns.joinToString { it.label }} marked as invalid")
        }

        return insights.take(5)
    }
}
