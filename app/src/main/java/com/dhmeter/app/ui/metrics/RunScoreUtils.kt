package com.dropindh.app.ui.metrics

import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.SeriesType
import java.util.Locale

private const val IMPACT_REF = 25f
private const val HARSHNESS_REF = 1.2f
private const val STABILITY_REF = 0.35f

private const val IMPACT_SERIES_REF = 5f
private const val HARSHNESS_SERIES_REF = 3f
private const val STABILITY_SERIES_REF = 0.5f

/**
 * Converts raw metric values to a burden score in range 0..100.
 * Lower burden score = smoother/cleaner run.
 */
fun normalizeBurdenScore(seriesType: SeriesType, value: Float): Float {
    val nonNegativeValue = value.coerceAtLeast(0f)
    val normalized = when (seriesType) {
        SeriesType.IMPACT_DENSITY -> (nonNegativeValue / (nonNegativeValue + IMPACT_REF)) * 100f
        SeriesType.HARSHNESS -> (nonNegativeValue / (nonNegativeValue + HARSHNESS_REF)) * 100f
        SeriesType.STABILITY -> (nonNegativeValue / (nonNegativeValue + STABILITY_REF)) * 100f
        SeriesType.SPEED_TIME -> value
    }
    return normalized.coerceIn(0f, 100f)
}

/**
 * Converts per-window series values to chart burden scale (0..100) using
 * the same references shown in the live monitor normalization.
 */
fun normalizeSeriesBurdenScore(seriesType: SeriesType, value: Float): Float {
    val nonNegativeValue = value.coerceAtLeast(0f)
    val normalized = when (seriesType) {
        SeriesType.IMPACT_DENSITY -> (nonNegativeValue / IMPACT_SERIES_REF) * 100f
        SeriesType.HARSHNESS -> (nonNegativeValue / HARSHNESS_SERIES_REF) * 100f
        SeriesType.STABILITY -> (nonNegativeValue / STABILITY_SERIES_REF) * 100f
        SeriesType.SPEED_TIME -> nonNegativeValue
    }
    return normalized.coerceIn(0f, 100f)
}

/**
 * Converts burden score (0..100, lower is better) into quality score (0..100, higher is better).
 */
fun burdenToQualityScore(burdenScore: Float): Float {
    return (100f - burdenScore).coerceIn(0f, 100f)
}

fun runMetricQualityScore(seriesType: SeriesType, rawValue: Float?): Float? {
    rawValue ?: return null
    return burdenToQualityScore(normalizeBurdenScore(seriesType, rawValue))
}

fun runOverallQualityScore(run: Run): Float? {
    val scores = listOfNotNull(
        runMetricQualityScore(SeriesType.IMPACT_DENSITY, run.impactScore),
        runMetricQualityScore(SeriesType.HARSHNESS, run.harshnessAvg),
        runMetricQualityScore(SeriesType.STABILITY, run.stabilityScore)
    )
    if (scores.isEmpty()) return null
    return scores.average().toFloat().coerceIn(0f, 100f)
}

fun formatScore0to100(value: Float?): String {
    value ?: return "--"
    return String.format(Locale.US, "%.2f", value.coerceIn(0f, 100f))
}

