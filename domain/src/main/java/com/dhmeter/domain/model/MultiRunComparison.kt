package com.dhmeter.domain.model

/**
 * Result of comparing multiple runs on the same track.
 */
data class MultiRunComparisonResult(
    val trackId: String,
    val runs: List<RunWithColor>,
    val metricComparisons: List<MultiMetricComparison>,
    val verdict: MultiRunVerdict,
    val sectionInsights: List<String> = emptyList(),
    val mapComparison: MapComparisonData? = null
)

/**
 * A run with an assigned color for visualization.
 */
data class RunWithColor(
    val run: Run,
    val color: Long, // Use Long to store ARGB color value
    val label: String // e.g., "Run 1", "Run 2"
)

/**
 * Comparison of a single metric across multiple runs.
 */
data class MultiMetricComparison(
    val metricName: String,
    val values: List<Float?>, // One value per run, null if not available
    val bestRunIndex: Int?, // Index of the run with best value (null if no valid values)
    val lowerIsBetter: Boolean
)

/**
 * Route overlay and split-time analysis for run-vs-run map comparison.
 * Baseline is always run index 0 (Run 1).
 */
data class MapComparisonData(
    val baselineRunIndex: Int = 0,
    val runs: List<MapComparisonRun>,
    val sections: List<MapSectionDelta>,
    val totalDeltaVsBaselineMs: List<Long?>, // per run, baseline = 0
    val hasMeasuredSplitTiming: Boolean
)

data class MapComparisonRun(
    val runId: String,
    val runLabel: String,
    val color: Long,
    val polyline: GpsPolyline
)

data class MapSectionDelta(
    val sectionIndex: Int,
    val startDistPct: Float,
    val endDistPct: Float,
    val sectionTimesMs: List<Long?>, // per run
    val deltaVsBaselineMs: List<Long?>, // per run
    val bestRunIndex: Int? // run with lowest section time
)

/**
 * Overall verdict for multi-run comparison.
 */
data class MultiRunVerdict(
    val bestRunIndex: Int?,
    val bestRunLabel: String,
    val title: String,
    val description: String,
    val type: Type
) {
    enum class Type {
        CLEAR_WINNER,  // One run is clearly best
        MIXED,         // Different runs excel at different metrics
        SIMILAR        // No significant differences
    }
    
    companion object {
        fun createClearWinner(bestIndex: Int, bestLabel: String, highlights: String): MultiRunVerdict = MultiRunVerdict(
            bestRunIndex = bestIndex,
            bestRunLabel = bestLabel,
            title = "$bestLabel was the smoothest",
            description = highlights,
            type = Type.CLEAR_WINNER
        )
        
        fun createMixed(highlights: String): MultiRunVerdict = MultiRunVerdict(
            bestRunIndex = null,
            bestRunLabel = "",
            title = "Mixed results",
            description = highlights,
            type = Type.MIXED
        )
        
        fun createSimilar(): MultiRunVerdict = MultiRunVerdict(
            bestRunIndex = null,
            bestRunLabel = "",
            title = "Similar performance",
            description = "No significant differences detected between runs",
            type = Type.SIMILAR
        )
    }
}
