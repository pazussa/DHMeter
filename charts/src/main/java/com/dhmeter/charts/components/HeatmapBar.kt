package com.dhmeter.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dhmeter.charts.model.HeatmapPoint

/**
 * Colors for heatmap display.
 */
object HeatmapColors {
    val Impact = listOf(
        Color(0xFF4CAF50),  // Green - low impact
        Color(0xFFFFEB3B),  // Yellow - moderate
        Color(0xFFFF9800),  // Orange - significant
        Color(0xFFF44336)   // Red - severe
    )

    val Harshness = listOf(
        Color(0xFF81C784),  // Light green - smooth
        Color(0xFFFFE082),  // Light yellow - light chatter
        Color(0xFFFFB74D),  // Light orange - moderate
        Color(0xFFE57373)   // Light red - rough
    )

    val Stability = listOf(
        Color(0xFF64B5F6),  // Light blue - excellent
        Color(0xFF4DD0E1),  // Cyan - good
        Color(0xFFFFD54F),  // Amber - moderate
        Color(0xFFE57373)   // Red - poor
    )

    val Speed = listOf(
        Color(0xFF1E88E5),  // Blue - slower
        Color(0xFF00ACC1),  // Cyan - moderate
        Color(0xFFFFCA28),  // Amber - fast
        Color(0xFFD32F2F)   // Red - very fast
    )

    val Slope = listOf(
        Color(0xFF90CAF9),  // Light blue - flat
        Color(0xFF7986CB),  // Indigo - moderate
        Color(0xFF5C6BC0)   // Deep indigo - steep
    )
}

/**
 * A heatmap bar showing metric intensity along the track.
 */
@Composable
fun HeatmapBar(
    points: List<HeatmapPoint>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    cornerRadius: Float = 4f,
    onPointSelected: ((xValue: Float, value: Float) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER")
    label: String? = null
) {
    val selectedXValue = remember(points) { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .clipToBounds()
            .pointerInput(points) {
                detectTapGestures { offset ->
                    if (points.isEmpty()) return@detectTapGestures
                    val sorted = points.sortedBy { it.x }
                    val minX = sorted.first().x
                    val maxX = sorted.last().x
                    val xRange = (maxX - minX).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val clampedX = offset.x.coerceIn(0f, widthPx)
                    val selectedX = minX + (clampedX / widthPx) * xRange
                    val selectedValue = interpolateHeatmapValueAtX(sorted, selectedX) ?: sorted.first().value
                    selectedXValue.value = selectedX
                    onPointSelected?.invoke(selectedX, selectedValue)
                }
            }
    ) {
        if (points.isEmpty()) return@Canvas

        val barHeight = size.height
        val barWidth = size.width
        val segmentWidth = barWidth / points.size
        val valueRange = (maxValue - minValue).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f
        val minX = points.minOfOrNull { it.x } ?: 0f
        val maxX = points.maxOfOrNull { it.x } ?: 1f
        val xRange = (maxX - minX).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f

        points.forEachIndexed { index, point ->
            // Normalize value to 0-1 range
            val normalizedValue = ((point.value - minValue) / valueRange)
                .coerceIn(0f, 1f)

            // Interpolate color based on value
            val color = interpolateColor(colors, normalizedValue)

            val x = index * segmentWidth

            // Draw segment
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(segmentWidth + 1f, barHeight), // +1 to avoid gaps
                cornerRadius = if (index == 0 || index == points.size - 1) {
                    CornerRadius(cornerRadius)
                } else {
                    CornerRadius.Zero
                }
            )
        }

        selectedXValue.value?.let { xValue ->
            val x = ((xValue - minX) / xRange).coerceIn(0f, 1f) * barWidth
            drawLine(
                color = Color.White.copy(alpha = 0.95f),
                start = Offset(x, 0f),
                end = Offset(x, barHeight),
                strokeWidth = 2f
            )
        }
    }
}

private fun interpolateHeatmapValueAtX(points: List<HeatmapPoint>, x: Float): Float? {
    if (points.isEmpty()) return null
    if (points.size == 1) return points.first().value
    if (x <= points.first().x) return points.first().value
    if (x >= points.last().x) return points.last().value

    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        if (x < a.x || x > b.x) continue
        val dx = b.x - a.x
        if (kotlin.math.abs(dx) < 1e-6f) return a.value
        val t = ((x - a.x) / dx).coerceIn(0f, 1f)
        return a.value + (b.value - a.value) * t
    }
    return points.minByOrNull { kotlin.math.abs(it.x - x) }?.value
}

/**
 * A gradient heatmap bar with smooth color transitions.
 */
@Composable
fun GradientHeatmapBar(
    points: List<HeatmapPoint>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 1f
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        if (points.isEmpty()) return@Canvas

        val barHeight = size.height
        val barWidth = size.width
        val minX = points.minOfOrNull { it.x } ?: 0f
        val maxX = points.maxOfOrNull { it.x } ?: 1f
        val xRange = (maxX - minX).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f
        val valueRange = (maxValue - minValue).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1f

        // Create color stops for gradient
        val colorStops = points.mapIndexed { _, point ->
            val normalizedValue = ((point.value - minValue) / valueRange)
                .coerceIn(0f, 1f)
            val position = ((point.x - minX) / xRange).coerceIn(0f, 1f)
            position to interpolateColor(colors, normalizedValue)
        }

        // Draw gradient bar
        if (colorStops.size >= 2) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = colorStops.toTypedArray()
                ),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

/**
 * Multi-layer heatmap showing multiple metrics.
 */
@Composable
fun MultiLayerHeatmap(
    layers: List<Pair<String, List<HeatmapPoint>>>,
    colorSchemes: List<List<Color>>,
    modifier: Modifier = Modifier,
    layerHeight: Int = 20,
    spacing: Int = 4
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(((layerHeight + spacing) * layers.size - spacing).dp)
    ) {
        layers.forEachIndexed { layerIndex, (_, points) ->
            if (points.isEmpty()) return@forEachIndexed

            val colors = colorSchemes.getOrElse(layerIndex) { HeatmapColors.Impact }
            val yOffset = layerIndex * (layerHeight + spacing).toFloat()
            val segmentWidth = size.width / points.size

            points.forEachIndexed { pointIndex, point ->
                val normalizedValue = point.value.coerceIn(0f, 1f)
                val color = interpolateColor(colors, normalizedValue)

                drawRect(
                    color = color,
                    topLeft = Offset(pointIndex * segmentWidth, yOffset),
                    size = Size(segmentWidth + 1f, layerHeight.toFloat())
                )
            }
        }
    }
}

/**
 * Interpolate between colors in a list based on a value (0-1).
 */
private fun interpolateColor(colors: List<Color>, value: Float): Color {
    if (colors.isEmpty()) return Color.Gray
    if (colors.size == 1) return colors[0]

    val scaledValue = value * (colors.size - 1)
    val lowerIndex = scaledValue.toInt().coerceIn(0, colors.size - 2)
    val upperIndex = (lowerIndex + 1).coerceIn(0, colors.size - 1)
    val fraction = scaledValue - lowerIndex

    val lower = colors[lowerIndex]
    val upper = colors[upperIndex]

    return Color(
        red = lower.red + (upper.red - lower.red) * fraction,
        green = lower.green + (upper.green - lower.green) * fraction,
        blue = lower.blue + (upper.blue - lower.blue) * fraction,
        alpha = lower.alpha + (upper.alpha - lower.alpha) * fraction
    )
}
