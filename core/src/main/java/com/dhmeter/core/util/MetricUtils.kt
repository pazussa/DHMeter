package com.dhmeter.core.util

/**
 * Utility functions for metric formatting.
 */
object MetricUtils {

    /**
     * Format G-force value.
     */
    fun formatG(gValue: Float): String {
        return String.format("%.1fg", gValue)
    }

    /**
     * Format acceleration in m/s².
     */
    fun formatAcceleration(accel: Float): String {
        return String.format("%.1f m/s²", accel)
    }

    /**
     * Format RMS value.
     */
    fun formatRms(rms: Float): String {
        return String.format("%.2f", rms)
    }

    /**
     * Format percentage.
     */
    fun formatPercent(value: Float): String {
        return String.format("%.1f%%", value)
    }

    /**
     * Format percentage change with sign.
     */
    fun formatPercentChange(value: Float): String {
        val sign = if (value >= 0) "+" else ""
        return String.format("%s%.1f%%", sign, value)
    }

    /**
     * Format integer count.
     */
    fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
            count >= 1_000 -> String.format("%.1fK", count / 1_000f)
            else -> count.toString()
        }
    }

    /**
     * Classify impact severity.
     */
    fun classifyImpact(gValue: Float): ImpactSeverity {
        return when {
            gValue < 2f -> ImpactSeverity.LIGHT
            gValue < 3f -> ImpactSeverity.MODERATE
            gValue < 4f -> ImpactSeverity.SIGNIFICANT
            else -> ImpactSeverity.SEVERE
        }
    }

    /**
     * Get descriptive text for harshness level.
     */
    fun describeHarshness(rms: Float): String {
        return when {
            rms < 1f -> "Smooth"
            rms < 2f -> "Light chatter"
            rms < 3f -> "Moderate chatter"
            rms < 5f -> "Rough"
            else -> "Very rough"
        }
    }

    /**
     * Get descriptive text for stability level.
     */
    fun describeStability(index: Float): String {
        return when {
            index < 0.05f -> "Excellent"
            index < 0.15f -> "Good"
            index < 0.30f -> "Moderate"
            else -> "Poor"
        }
    }
}

enum class ImpactSeverity {
    LIGHT, MODERATE, SIGNIFICANT, SEVERE
}
