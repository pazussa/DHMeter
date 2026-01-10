package com.dhmeter.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhmeter.charts.model.ChartEventMarker
import com.dhmeter.charts.model.ChartMarkerIcon

/**
 * Event marker colors by type.
 */
object EventMarkerColors {
    val Impact = Color(0xFFF44336)      // Red
    val Landing = Color(0xFFFF9800)     // Orange
    val Jump = Color(0xFF2196F3)        // Blue
    val Turn = Color(0xFF9C27B0)        // Purple
    val Brake = Color(0xFFFFEB3B)       // Yellow
    val Acceleration = Color(0xFF4CAF50) // Green
}

/**
 * Component to display event markers on a chart.
 */
@Composable
fun EventMarkers(
    markers: List<ChartEventMarker>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    xMin: Float = 0f,
    xMax: Float = 100f
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val xRange = xMax - xMin

        markers.forEach { marker ->
            val xPos = (marker.x - xMin) / xRange * chartWidth

            // Draw marker icon
            drawMarkerIcon(
                icon = marker.icon,
                center = Offset(xPos, chartHeight / 2),
                color = marker.color,
                size = 12f
            )

            // Draw label if enabled
            if (showLabels && marker.label.isNotEmpty()) {
                val textResult = textMeasurer.measure(
                    marker.label,
                    TextStyle(fontSize = 8.sp, color = marker.color)
                )
                drawText(
                    textResult,
                    topLeft = Offset(
                        xPos - textResult.size.width / 2,
                        chartHeight / 2 + 14f
                    )
                )
            }
        }
    }
}

/**
 * Draws a marker icon.
 */
private fun DrawScope.drawMarkerIcon(
    icon: ChartMarkerIcon,
    center: Offset,
    color: Color,
    size: Float
) {
    when (icon) {
        ChartMarkerIcon.CIRCLE -> {
            drawCircle(
                color = color,
                radius = size / 2,
                center = center
            )
        }

        ChartMarkerIcon.TRIANGLE -> {
            val path = Path().apply {
                moveTo(center.x, center.y - size / 2)
                lineTo(center.x - size / 2, center.y + size / 2)
                lineTo(center.x + size / 2, center.y + size / 2)
                close()
            }
            drawPath(path, color)
        }

        ChartMarkerIcon.SQUARE -> {
            val halfSize = size / 2
            drawRect(
                color = color,
                topLeft = Offset(center.x - halfSize, center.y - halfSize),
                size = androidx.compose.ui.geometry.Size(size, size)
            )
        }

        ChartMarkerIcon.DIAMOND -> {
            val path = Path().apply {
                moveTo(center.x, center.y - size / 2)
                lineTo(center.x + size / 2, center.y)
                lineTo(center.x, center.y + size / 2)
                lineTo(center.x - size / 2, center.y)
                close()
            }
            drawPath(path, color)
        }
    }
}

/**
 * Event timeline showing events along the track.
 */
@Composable
fun EventTimeline(
    markers: List<ChartEventMarker>,
    modifier: Modifier = Modifier,
    xMin: Float = 0f,
    xMax: Float = 100f,
    lineColor: Color = Color.Gray.copy(alpha = 0.3f),
    showConnectors: Boolean = true
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val xRange = xMax - xMin
        val timelineY = chartHeight / 2

        // Draw timeline base
        drawLine(
            color = lineColor,
            start = Offset(0f, timelineY),
            end = Offset(chartWidth, timelineY),
            strokeWidth = 2f
        )

        // Draw markers
        markers.forEach { marker ->
            val xPos = (marker.x - xMin) / xRange * chartWidth

            // Draw connector line
            if (showConnectors) {
                drawLine(
                    color = marker.color.copy(alpha = 0.5f),
                    start = Offset(xPos, 10f),
                    end = Offset(xPos, timelineY - 8f),
                    strokeWidth = 1f
                )
            }

            // Draw marker circle
            drawCircle(
                color = marker.color,
                radius = 8f,
                center = Offset(xPos, timelineY)
            )
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(xPos, timelineY)
            )

            // Draw label below
            val textResult = textMeasurer.measure(
                marker.label,
                TextStyle(fontSize = 9.sp, color = marker.color)
            )
            drawText(
                textResult,
                topLeft = Offset(
                    (xPos - textResult.size.width / 2).coerceIn(0f, chartWidth - textResult.size.width),
                    timelineY + 12f
                )
            )
        }

        // Draw percentage markers
        for (i in 0..4) {
            val xPos = chartWidth * i / 4
            val label = "${i * 25}%"
            val textResult = textMeasurer.measure(
                label,
                TextStyle(fontSize = 8.sp, color = lineColor)
            )
            drawText(
                textResult,
                topLeft = Offset(xPos - textResult.size.width / 2, 0f)
            )

            // Draw tick mark
            drawLine(
                color = lineColor,
                start = Offset(xPos, timelineY - 4f),
                end = Offset(xPos, timelineY + 4f),
                strokeWidth = 1f
            )
        }
    }
}
