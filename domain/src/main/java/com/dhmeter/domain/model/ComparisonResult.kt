package com.dhmeter.domain.model

/**
 * Result of comparing two runs on the same track.
 */
data class ComparisonResult(
    val trackId: String,
    val runA: Run,
    val runB: Run,
    val impactComparison: MetricComparison?,
    val harshnessComparison: MetricComparison?,
    val stabilityComparison: MetricComparison?,
    val landingComparison: MetricComparison?,
    val durationComparison: MetricComparison?,
    val verdict: Verdict,
    val sectionInsights: List<String> = emptyList()
)

/**
 * Comparison of a single metric between two runs.
 */
data class MetricComparison(
    val valueA: Float,
    val valueB: Float,
    val deltaPercent: Float // (B - A) / A * 100, negative means B improved
)

/**
 * Overall verdict of the comparison.
 */
data class Verdict(
    val type: Type,
    val title: String,
    val description: String
) {
    enum class Type {
        BETTER,   // Run B is clearly better
        WORSE,    // Run B is clearly worse
        MIXED,    // Some metrics better, some worse
        SIMILAR   // No significant difference
    }
    
    companion object {
        fun createBetter(highlights: String): Verdict = Verdict(
            type = Type.BETTER,
            title = "Run B was better",
            description = highlights
        )
        
        fun createWorse(highlights: String): Verdict = Verdict(
            type = Type.WORSE,
            title = "Run B was more punishing",
            description = highlights
        )
        
        fun createMixed(highlights: String): Verdict = Verdict(
            type = Type.MIXED,
            title = "Mixed results",
            description = highlights
        )
        
        fun createSimilar(): Verdict = Verdict(
            type = Type.SIMILAR,
            title = "Similar performance",
            description = "No significant differences detected between runs"
        )
    }
}
