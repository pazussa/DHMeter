package com.dhmeter.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhmeter.charts.model.AxisConfig
import com.dhmeter.charts.model.ChartEventMarker
import com.dhmeter.charts.model.ChartSeries

/**
 * A line chart component for comparing multiple runs.
 * X-axis represents distance percentage (0-100%).
 */
@Composable
fun ComparisonLineChart(
    series: List<ChartSeries>,
    xAxisConfig: AxisConfig,
    yAxisConfig: AxisConfig,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showLegend: Boolean = true,
    eventMarkers: List<ChartEventMarker> = emptyList(),
    onPointSelected: ((seriesIndex: Int, pointIndex: Int, value: Float) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val gridColor = Color.Gray.copy(alpha = 0.2f)
    val axisColor = Color.Gray.copy(alpha = 0.5f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(series) {
                detectTapGestures { offset ->
                    // Find nearest point
                    val chartWidth = size.width - 60f
                    val chartHeight = size.height - 40f
                    val chartLeft = 50f

                    val xPercent = ((offset.x - chartLeft) / chartWidth)
                        .coerceIn(0f, 1f) * 100f

                    series.forEachIndexed { seriesIdx, s ->
                        val nearestIdx = s.points.indexOfFirst { it.x >= xPercent }
                        if (nearestIdx >= 0) {
                            selectedPoint = seriesIdx to nearestIdx
                            onPointSelected?.invoke(seriesIdx, nearestIdx, s.points[nearestIdx].y)
                        }
                    }
                }
            }
    ) {
        val chartWidth = size.width - 60f
        val chartHeight = size.height - 40f
        val chartLeft = 50f
        val chartTop = 10f

        // Draw grid
        if (showGrid) {
            drawGrid(
                chartLeft = chartLeft,
                chartTop = chartTop,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                xAxisConfig = xAxisConfig,
                yAxisConfig = yAxisConfig,
                gridColor = gridColor
            )
        }

        // Draw axes
        drawLine(
            color = axisColor,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartLeft, chartTop + chartHeight),
            strokeWidth = 1f
        )
        drawLine(
            color = axisColor,
            start = Offset(chartLeft, chartTop + chartHeight),
            end = Offset(chartLeft + chartWidth, chartTop + chartHeight),
            strokeWidth = 1f
        )

        // Draw Y-axis labels
        val yRange = yAxisConfig.max - yAxisConfig.min
        for (i in 0..yAxisConfig.gridLines) {
            val yValue = yAxisConfig.min + (yRange * i / yAxisConfig.gridLines)
            val yPos = chartTop + chartHeight - (chartHeight * i / yAxisConfig.gridLines)

            val label = yAxisConfig.format(yValue)
            val textResult = textMeasurer.measure(
                label,
                TextStyle(fontSize = 10.sp, color = axisColor)
            )
            drawText(
                textResult,
                topLeft = Offset(5f, yPos - textResult.size.height / 2)
            )
        }

        // Draw X-axis labels
        val xRange = xAxisConfig.max - xAxisConfig.min
        for (i in 0..4) {
            val xValue = xAxisConfig.min + (xRange * i / 4)
            val xPos = chartLeft + (chartWidth * i / 4)

            val label = xAxisConfig.format(xValue)
            val textResult = textMeasurer.measure(
                label,
                TextStyle(fontSize = 10.sp, color = axisColor)
            )
            drawText(
                textResult,
                topLeft = Offset(xPos - textResult.size.width / 2, chartTop + chartHeight + 5f)
            )
        }

        // Draw event markers
        eventMarkers.forEach { marker ->
            val xPos = chartLeft + (chartWidth * (marker.x - xAxisConfig.min) / xRange)
            drawLine(
                color = marker.color.copy(alpha = 0.5f),
                start = Offset(xPos, chartTop),
                end = Offset(xPos, chartTop + chartHeight),
                strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(4f, 4f)
                )
            )
            drawCircle(
                color = marker.color,
                radius = 6f,
                center = Offset(xPos, chartTop + 10f)
            )
        }

        // Draw series
        series.forEach { s ->
            if (s.points.isEmpty()) return@forEach

            val path = Path()
            var started = false

            s.points.forEach { point ->
                val xPos = chartLeft + (chartWidth * (point.x - xAxisConfig.min) / xRange)
                val yPos = chartTop + chartHeight - (chartHeight * (point.y - yAxisConfig.min) / yRange)

                if (!started) {
                    path.moveTo(xPos, yPos.coerceIn(chartTop, chartTop + chartHeight))
                    started = true
                } else {
                    path.lineTo(xPos, yPos.coerceIn(chartTop, chartTop + chartHeight))
                }
            }

            drawPath(
                path = path,
                color = s.color,
                style = Stroke(
                    width = s.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Draw selected point indicator
        selectedPoint?.let { (seriesIdx, pointIdx) ->
            if (seriesIdx < series.size && pointIdx < series[seriesIdx].points.size) {
                val point = series[seriesIdx].points[pointIdx]
                val xPos = chartLeft + (chartWidth * (point.x - xAxisConfig.min) / xRange)
                val yPos = chartTop + chartHeight - (chartHeight * (point.y - yAxisConfig.min) / yRange)

                drawCircle(
                    color = series[seriesIdx].color,
                    radius = 8f,
                    center = Offset(xPos, yPos.coerceIn(chartTop, chartTop + chartHeight))
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(xPos, yPos.coerceIn(chartTop, chartTop + chartHeight))
                )
            }
        }
    }
}

private fun DrawScope.drawGrid(
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    xAxisConfig: AxisConfig,
    yAxisConfig: AxisConfig,
    gridColor: Color
) {
    // Horizontal grid lines
    for (i in 0..yAxisConfig.gridLines) {
        val y = chartTop + (chartHeight * i / yAxisConfig.gridLines)
        drawLine(
            color = gridColor,
            start = Offset(chartLeft, y),
            end = Offset(chartLeft + chartWidth, y),
            strokeWidth = 1f
        )
    }

    // Vertical grid lines
    for (i in 0..4) {
        val x = chartLeft + (chartWidth * i / 4)
        drawLine(
            color = gridColor,
            start = Offset(x, chartTop),
            end = Offset(x, chartTop + chartHeight),
            strokeWidth = 1f
        )
    }
}
