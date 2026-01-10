package com.dhmeter.charts.model

import androidx.compose.ui.graphics.Color

/**
 * Data point for charts.
 */
data class ChartPoint(
    val x: Float,
    val y: Float
)

/**
 * A line series for comparison charts.
 */
data class ChartSeries(
    val label: String,
    val points: List<ChartPoint>,
    val color: Color,
    val strokeWidth: Float = 2f
)

/**
 * Configuration for chart axis.
 */
data class AxisConfig(
    val min: Float,
    val max: Float,
    val gridLines: Int = 5,
    val label: String = "",
    val format: (Float) -> String = { "%.1f".format(it) }
)

/**
 * Event marker for charts.
 */
data class ChartEventMarker(
    val x: Float,
    val label: String,
    val color: Color,
    val icon: ChartMarkerIcon = ChartMarkerIcon.CIRCLE
)

enum class ChartMarkerIcon {
    CIRCLE, TRIANGLE, SQUARE, DIAMOND
}

/**
 * Heatmap data point.
 */
data class HeatmapPoint(
    val x: Float,
    val value: Float,
    val category: Int = 0
)
