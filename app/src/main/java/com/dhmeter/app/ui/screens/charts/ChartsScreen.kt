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
            seriesSelector = { it.impactSeries },
            yAxisLabel = "Impact (g^2)"
        )

        // Harshness vs distPct
        MultiRunChartSection(
            title = "Harshness vs Distance %",
            runs = uiState.runs,
            seriesSelector = { it.harshnessSeries },
            yAxisLabel = "RMS"
        )

        // Stability vs distPct
        MultiRunChartSection(
            title = "Stability vs Distance %",
            runs = uiState.runs,
            seriesSelector = { it.stabilitySeries },
            yAxisLabel = "Variance"
        )

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
                        maxValue = 5f
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
    seriesSelector: (RunChartData) -> RunSeries?,
    yAxisLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )

        val chartSeriesList = runs.mapNotNull { run ->
            val series = seriesSelector(run)
            series?.toChartPoints()?.takeIf { it.isNotEmpty() }?.let { points ->
                ChartSeries(run.runLabel, points, run.color)
            }
        }

        if (chartSeriesList.isNotEmpty()) {
            val allYValues = chartSeriesList.flatMap { it.points.map { p -> p.y } }
            if (allYValues.isEmpty()) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val rawMin = allYValues.minOrNull() ?: 0f
            val rawMax = allYValues.maxOrNull() ?: 1f
            val hasFlatRange = (rawMax - rawMin) < 1e-6f
            val padding = if (hasFlatRange) {
                if (rawMax == 0f) 1f else kotlin.math.abs(rawMax) * 0.1f
            } else {
                0f
            }
            val yMin = rawMin - padding
            val yMax = rawMax + padding

            ComparisonLineChart(
                series = chartSeriesList,
                xAxisConfig = AxisConfig(0f, 100f, label = "Distance %"),
                yAxisConfig = AxisConfig(yMin, yMax, label = yAxisLabel),
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
        } else {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Extension function to convert RunSeries to list of ChartPoint
 */
private fun RunSeries.toChartPoints(): List<ChartPoint> {
    return (0 until pointCount).mapNotNull { i ->
        ChartPoint(points[i * 2], points[i * 2 + 1])
            .takeIf { it.x.isFinite() && it.y.isFinite() }
    }
}

/**
 * Extension function to convert RunSeries to list of HeatmapPoint
 */
private fun RunSeries.toHeatmapPoints(): List<HeatmapPoint> {
    return (0 until pointCount).mapNotNull { i ->
        HeatmapPoint(points[i * 2], points[i * 2 + 1])
            .takeIf { it.x.isFinite() && it.value.isFinite() }
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

