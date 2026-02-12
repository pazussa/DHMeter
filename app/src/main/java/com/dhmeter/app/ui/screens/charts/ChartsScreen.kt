package com.dhmeter.app.ui.screens.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.ui.metrics.normalizeSeriesBurdenScore
import com.dhmeter.charts.components.ComparisonLineChart
import com.dhmeter.charts.components.EventMarkers
import com.dhmeter.charts.components.HeatmapBar
import com.dhmeter.charts.components.HeatmapColors
import com.dhmeter.charts.model.AxisConfig
import com.dhmeter.charts.model.ChartSeries
import com.dhmeter.charts.model.ChartPoint
import com.dhmeter.charts.model.ChartEventMarker
import com.dhmeter.charts.model.ChartMarkerIcon
import com.dhmeter.charts.model.HeatmapPoint
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.EventType
import com.dhmeter.domain.model.SeriesType
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    trackId: String,
    runIds: List<String>,
    onBack: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(trackId, runIds) {
        viewModel.loadChartData(trackId, runIds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charts (${runIds.size} runs)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                ChartsContent(
                    uiState = uiState,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun ChartsContent(
    uiState: ChartsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Legend for runs
        if (uiState.runs.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.runs.forEach { run ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .padding(1.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = run.color)
                            }
                        }
                        Text(
                            text = run.runLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Impact Density vs distPct
        MultiRunChartSection(
            title = "Impact Density vs Distance %",
            runs = uiState.runs,
            seriesSelector = { it.impactSeries }
        )

        // Harshness vs distPct
        MultiRunChartSection(
            title = "Harshness vs Distance %",
            runs = uiState.runs,
            seriesSelector = { it.harshnessSeries }
        )

        // Stability vs distPct
        MultiRunChartSection(
            title = "Stability vs Distance %",
            runs = uiState.runs,
            seriesSelector = { it.stabilitySeries }
        )

        SpeedComparisonSection(runs = uiState.runs)

        // Events over distPct - Combined view
        if (uiState.runs.any { it.events.isNotEmpty() }) {
            Text(
                text = "Events over Distance %",
                style = MaterialTheme.typography.titleMedium
            )
            
            uiState.runs.forEach { run ->
                if (run.events.isNotEmpty()) {
                    Text(
                        text = "${run.runLabel} Events",
                        style = MaterialTheme.typography.labelMedium,
                        color = run.color
                    )
                    EventMarkers(
                        markers = run.events.toChartMarkers(run.color),
                        xMin = 0f,
                        xMax = 100f,
                        showLabels = true
                    )
                }
            }
        }

        // Heatmap lineal for each run
        Text(
            text = "Heatmap Comparison",
            style = MaterialTheme.typography.titleMedium
        )
        
        uiState.runs.forEach { run ->
            run.impactSeries?.let { series ->
                if (series.pointCount > 0) {
                    Text(
                        text = "Impact Heatmap - ${run.runLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = run.color
                    )
                    HeatmapBar(
                        points = series.toHeatmapPoints(),
                        colors = HeatmapColors.Impact,
                        minValue = 0f,
                        maxValue = 100f
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MultiRunChartSection(
    title: String,
    runs: List<RunChartData>,
    seriesSelector: (RunChartData) -> RunSeries?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Burden score: 0 = smoother, 100 = more punishing (lower is better).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        val chartSeriesList = runs.mapNotNull { run ->
            val series = seriesSelector(run)
            series?.toChartPoints()?.takeIf { it.isNotEmpty() }?.let { points ->
                ChartSeries(run.runLabel, points, run.color)
            }
        }

        if (chartSeriesList.isNotEmpty()) {
            ComparisonLineChart(
                series = chartSeriesList,
                xAxisConfig = AxisConfig(0f, 100f, label = "Distance %"),
                yAxisConfig = AxisConfig(0f, 100f, label = "Score (0-100)"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            
            // Show event markers below the chart
            val allMarkers = runs.flatMap { run ->
                run.events.map { event -> event.toChartMarker(run.color) }
            }
            
            if (allMarkers.isNotEmpty()) {
                EventMarkers(
                    markers = allMarkers,
                    xMin = 0f,
                    xMax = 100f,
                    showLabels = false
                )
            }

            // Delta chart gives higher precision for A/B comparisons.
            if (runs.size == 2) {
                val runA = runs[0]
                val runB = runs[1]
                val deltaPoints = buildDeltaPoints(
                    pointsA = seriesSelector(runA)?.toChartPoints().orEmpty(),
                    pointsB = seriesSelector(runB)?.toChartPoints().orEmpty()
                )
                if (deltaPoints.isNotEmpty()) {
                    val maxAbsDelta = deltaPoints.maxOfOrNull { abs(it.y) } ?: 0f
                    val axis = max(10f, (kotlin.math.ceil(maxAbsDelta / 10f) * 10f).toFloat())
                        .coerceAtMost(100f)
                    val peak = deltaPoints.maxByOrNull { abs(it.y) }

                    Text(
                        text = "Delta (${runB.runLabel} - ${runA.runLabel})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    ComparisonLineChart(
                        series = listOf(
                            ChartSeries(
                                label = "Delta",
                                points = deltaPoints,
                                color = Color(0xFF8E24AA)
                            )
                        ),
                        xAxisConfig = AxisConfig(0f, 100f, label = "Distance %"),
                        yAxisConfig = AxisConfig(-axis, axis, label = "Delta"),
                        showLegend = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    peak?.let {
                        Text(
                            text = "Peak delta ${String.format("%.1f", it.y)} at ${String.format("%.0f", it.x)}%. Positive = ${runB.runLabel} harsher.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpeedComparisonSection(
    runs: List<RunChartData>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Speed vs Distance %",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Speed derived from timing profile and total distance.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        val chartSeriesList = runs.mapNotNull { run ->
            val distanceMeters = run.run?.distanceMeters
            val points = run.speedSeries
                ?.toSpeedChartPoints(distanceMeters)
                .orEmpty()
                .ifEmpty { fallbackSpeedChartPoints(run.run?.avgSpeed) }
            if (points.isNotEmpty()) {
                ChartSeries(run.runLabel, points, run.color)
            } else {
                null
            }
        }

        if (chartSeriesList.isEmpty()) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxSpeed = chartSeriesList.flatMap { it.points }.maxOfOrNull { it.y } ?: 0f
            val axisMax = (kotlin.math.ceil(maxSpeed / 5f) * 5f).coerceAtLeast(10f)

            ComparisonLineChart(
                series = chartSeriesList,
                xAxisConfig = AxisConfig(0f, 100f, label = "Distance %"),
                yAxisConfig = AxisConfig(0f, axisMax, label = "km/h"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

/**
 * Extension function to convert RunSeries to list of ChartPoint
 */
private fun RunSeries.toChartPoints(): List<ChartPoint> {
    return (0 until pointCount).mapNotNull { i ->
        ChartPoint(points[i * 2], normalizeToScore(seriesType, points[i * 2 + 1]))
            .takeIf { it.x.isFinite() && it.y.isFinite() }
    }
}

private fun RunSeries.toSpeedChartPoints(distanceMeters: Float?): List<ChartPoint> {
    if (seriesType != SeriesType.SPEED_TIME) return emptyList()
    val totalDistanceM = distanceMeters?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    if (pointCount < 2) return emptyList()

    val result = ArrayList<ChartPoint>(pointCount - 1)
    for (i in 1 until pointCount) {
        val prevX = points[(i - 1) * 2]
        val prevT = points[(i - 1) * 2 + 1]
        val currX = points[i * 2]
        val currT = points[i * 2 + 1]
        val distPctDelta = (currX - prevX).coerceAtLeast(0f)
        val timeDeltaSec = (currT - prevT).coerceAtLeast(0f)
        if (timeDeltaSec <= 1e-3f) continue

        val distM = totalDistanceM * (distPctDelta / 100f)
        val speedMps = distM / timeDeltaSec
        if (!speedMps.isFinite()) continue

        val midX = (prevX + currX) / 2f
        result.add(ChartPoint(midX, speedMps * 3.6f))
    }
    return result
}

private fun fallbackSpeedChartPoints(avgSpeedMps: Float?): List<ChartPoint> {
    val speedMps = avgSpeedMps?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val speedKmh = speedMps * 3.6f
    return listOf(
        ChartPoint(0f, speedKmh),
        ChartPoint(100f, speedKmh)
    )
}

/**
 * Extension function to convert RunSeries to list of HeatmapPoint
 */
private fun RunSeries.toHeatmapPoints(): List<HeatmapPoint> {
    return (0 until pointCount).mapNotNull { i ->
        HeatmapPoint(points[i * 2], normalizeToScore(seriesType, points[i * 2 + 1]))
            .takeIf { it.x.isFinite() && it.value.isFinite() }
    }
}

private fun normalizeToScore(seriesType: SeriesType, value: Float): Float {
    return normalizeSeriesBurdenScore(seriesType, value)
}

private fun buildDeltaPoints(
    pointsA: List<ChartPoint>,
    pointsB: List<ChartPoint>
): List<ChartPoint> {
    val size = minOf(pointsA.size, pointsB.size)
    if (size == 0) return emptyList()
    return (0 until size).map { index ->
        val x = ((pointsA[index].x + pointsB[index].x) / 2f).coerceIn(0f, 100f)
        val delta = (pointsB[index].y - pointsA[index].y).coerceIn(-100f, 100f)
        ChartPoint(x = x, y = delta)
    }
}

/**
 * Convert list of RunEvents to ChartEventMarkers with custom color
 */
private fun List<RunEvent>.toChartMarkers(runColor: Color): List<ChartEventMarker> {
    return map { event ->
        ChartEventMarker(
            x = event.distPct,
            icon = event.type.toMarkerIcon(),
            color = runColor,
            label = event.type.take(3)
        )
    }
}

/**
 * Convert a single RunEvent to ChartEventMarker with custom color
 */
private fun RunEvent.toChartMarker(runColor: Color): ChartEventMarker {
    return ChartEventMarker(
        x = distPct,
        icon = type.toMarkerIcon(),
        color = runColor,
        label = ""
    )
}

/**
 * Map event type string to ChartMarkerIcon
 */
private fun String.toMarkerIcon(): ChartMarkerIcon {
    return when (this) {
        EventType.IMPACT_PEAK -> ChartMarkerIcon.TRIANGLE
        EventType.LANDING -> ChartMarkerIcon.DIAMOND
        EventType.HARSHNESS_BURST -> ChartMarkerIcon.SQUARE
        else -> ChartMarkerIcon.CIRCLE
    }
}

/**
 * Map event type string to color
 */
private fun String.toMarkerColor(): Color {
    return when (this) {
        EventType.IMPACT_PEAK -> Color(0xFFF44336)      // Red
        EventType.LANDING -> Color(0xFFFF9800)          // Orange
        EventType.HARSHNESS_BURST -> Color(0xFFFFEB3B)  // Yellow
        else -> Color(0xFF9C27B0)                        // Purple
    }
}


