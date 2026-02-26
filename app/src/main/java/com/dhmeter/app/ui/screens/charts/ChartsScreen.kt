package com.dropindh.app.ui.screens.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.ui.charts.distanceFromPct
import com.dropindh.app.ui.charts.toAccelerationChartPointsMeters
import com.dropindh.app.ui.charts.toBurdenChartPointsMeters
import com.dropindh.app.ui.charts.toSpeedChartPointsMeters
import com.dropindh.app.ui.charts.toSpeedHeatmapPointsMeters
import com.dropindh.app.ui.metrics.normalizeSeriesBurdenScore
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
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.dhTopBarColors

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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = { Text(tr("Charts (${runIds.size} runs)", "Gráficas (${runIds.size} bajadas)")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Atrás"))
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
                        text = uiState.error ?: tr("Unknown error", "Error desconocido"),
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
            title = tr("Impact Density vs Distance (m)", "Densidad de impacto vs Distancia (m)"),
            runs = uiState.runs,
            seriesSelector = { it.impactSeries }
        )

        // Harshness vs distPct
        MultiRunChartSection(
            title = tr("Harshness vs Distance (m)", "Vibración vs Distancia (m)"),
            runs = uiState.runs,
            seriesSelector = { it.harshnessSeries }
        )

        // Stability vs distPct
        MultiRunChartSection(
            title = tr("Stability vs Distance (m)", "Estabilidad vs Distancia (m)"),
            runs = uiState.runs,
            seriesSelector = { it.stabilitySeries }
        )

        SpeedComparisonSection(runs = uiState.runs)
        AccelerationComparisonSection(runs = uiState.runs)

        // Events over distPct - Combined view
        if (uiState.runs.any { it.events.isNotEmpty() }) {
            Text(
                text = tr("Events over Distance (m)", "Eventos sobre Distancia (m)"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = tr(
                    "Legend: IMP = Impact, LAN = Landing, VIB = Vibration event",
                    "Leyenda: IMP = Impacto, LAN = Aterrizaje, VIB = Evento de vibracion"
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            uiState.runs.forEach { run ->
                if (run.events.isNotEmpty()) {
                    Text(
                        text = tr("${run.runLabel} Events", "Eventos de ${run.runLabel}"),
                        style = MaterialTheme.typography.labelMedium,
                        color = run.color
                    )
                    EventMarkers(
                        markers = run.events.toChartMarkers(run.color, run.run?.distanceMeters),
                        xMin = 0f,
                        xMax = run.run?.distanceMeters?.coerceAtLeast(10f) ?: 100f,
                        showLabels = true
                    )
                }
            }
        }

        // Heatmap by speed for each run
        Text(
            text = tr("Speed Heatmap Comparison", "Comparación de mapa de calor por velocidad"),
            style = MaterialTheme.typography.titleMedium
        )
        
        uiState.runs.forEach { run ->
            val distanceMeters = run.run?.distanceMeters
            val speedPoints = run.speedSeries
                ?.toSpeedHeatmapPointsMeters(distanceMeters)
                .orEmpty()
                .ifEmpty { fallbackSpeedHeatmapPoints(run.run?.avgSpeed, distanceMeters) }

            if (speedPoints.isNotEmpty()) {
                val maxSpeed = speedPoints.maxOfOrNull { it.value } ?: 0f
                Text(
                    text = tr(
                        "Speed Heatmap - ${run.runLabel}",
                        "Mapa de calor de velocidad - ${run.runLabel}"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = run.color
                )
                HeatmapBar(
                    points = speedPoints,
                    colors = HeatmapColors.Speed,
                    minValue = 0f,
                    maxValue = maxSpeed.coerceAtLeast(10f)
                )
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
            text = tr(
                "Lower values usually mean a smoother section.",
                "Valores mas bajos suelen indicar una seccion mas suave."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        val chartSeriesList = runs.mapNotNull { run ->
            val series = seriesSelector(run)
            series?.toChartPoints(run.run?.distanceMeters)?.takeIf { it.isNotEmpty() }?.let { points ->
                ChartSeries(run.runLabel, points, run.color)
            }
        }

        if (chartSeriesList.isNotEmpty()) {
            val axis = meterAxisConfig(chartSeriesList.flatMap { it.points })
            ComparisonLineChart(
                series = chartSeriesList,
                xAxisConfig = axis,
                yAxisConfig = AxisConfig(0f, 100f, label = tr("Score (0-100)", "Puntaje (0-100)")),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            
            // Show event markers below the chart
            val allMarkers = runs.flatMap { run ->
                run.events.mapNotNull { event -> event.toChartMarker(run.color, run.run?.distanceMeters) }
            }
            
            if (allMarkers.isNotEmpty()) {
                EventMarkers(
                    markers = allMarkers,
                    xMin = 0f,
                    xMax = axis.max,
                    showLabels = false
                )
            }

        } else {
            Text(
                text = tr("No data available", "No hay datos disponibles"),
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
            text = tr("Speed vs Distance (m)", "Velocidad vs Distancia (m)"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = tr(
                "Tap a point to compare where speed changes between runs.",
                "Toca un punto para comparar donde cambia la velocidad entre bajadas."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        val chartSeriesList = runs.mapNotNull { run ->
            val distanceMeters = run.run?.distanceMeters
            val points = run.speedSeries
                ?.toSpeedChartPointsMeters(distanceMeters)
                .orEmpty()
                .ifEmpty { fallbackSpeedChartPoints(run.run?.avgSpeed, distanceMeters) }
            if (points.isNotEmpty()) {
                ChartSeries(run.runLabel, points, run.color)
            } else {
                null
            }
        }

        if (chartSeriesList.isEmpty()) {
            Text(
                text = tr("No data available", "No hay datos disponibles"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxSpeed = chartSeriesList.flatMap { it.points }.maxOfOrNull { it.y } ?: 0f
            val axisMax = (kotlin.math.ceil(maxSpeed / 5f) * 5f).coerceAtLeast(10f)
            val axis = meterAxisConfig(chartSeriesList.flatMap { it.points })

            ComparisonLineChart(
                series = chartSeriesList,
                xAxisConfig = axis,
                yAxisConfig = AxisConfig(0f, axisMax, label = "km/h"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
private fun AccelerationComparisonSection(
    runs: List<RunChartData>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = tr("Acceleration vs Distance (m)", "Aceleracion vs Distancia (m)"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = tr(
                "Highlights braking and acceleration zones.",
                "Destaca zonas de frenada y aceleracion."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        val chartSeriesList = runs.mapNotNull { run ->
            val distanceMeters = run.run?.distanceMeters
            val points = run.speedSeries
                ?.toAccelerationChartPointsMeters(distanceMeters)
                .orEmpty()
            if (points.isNotEmpty()) {
                ChartSeries(run.runLabel, points, run.color)
            } else {
                null
            }
        }

        if (chartSeriesList.isEmpty()) {
            Text(
                text = tr("No data available", "No hay datos disponibles"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxAbsAcceleration = chartSeriesList
                .flatMap { it.points }
                .maxOfOrNull { kotlin.math.abs(it.y) } ?: 0f
            val axisLimit = (kotlin.math.ceil(maxAbsAcceleration * 2f) / 2f).coerceAtLeast(0.5f)
            val axis = meterAxisConfig(chartSeriesList.flatMap { it.points })

            ComparisonLineChart(
                series = chartSeriesList,
                xAxisConfig = axis,
                yAxisConfig = AxisConfig(-axisLimit, axisLimit, label = "m/s²"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

private fun RunSeries.toChartPoints(distanceMeters: Float?): List<ChartPoint> {
    return toBurdenChartPointsMeters(distanceMeters)
        .map { point -> ChartPoint(point.x, normalizeToScore(seriesType, point.y)) }
}

private fun fallbackSpeedChartPoints(
    avgSpeedMps: Float?,
    distanceMeters: Float?
): List<ChartPoint> {
    val speedMps = avgSpeedMps?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val totalDistance = distanceMeters?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val speedKmh = speedMps * 3.6f
    return listOf(
        ChartPoint(0f, speedKmh),
        ChartPoint(totalDistance, speedKmh)
    )
}

private fun fallbackSpeedHeatmapPoints(
    avgSpeedMps: Float?,
    distanceMeters: Float?
): List<HeatmapPoint> {
    val speedMps = avgSpeedMps?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val totalDistance = distanceMeters?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val speedKmh = speedMps * 3.6f
    return listOf(
        HeatmapPoint(0f, speedKmh),
        HeatmapPoint(totalDistance, speedKmh)
    )
}

private fun meterAxisConfig(points: List<ChartPoint>): AxisConfig {
    val maxX = points.maxOfOrNull { it.x }?.coerceAtLeast(1f) ?: 1f
    val axisMax = (kotlin.math.ceil(maxX / 10f) * 10f).coerceAtLeast(10f)
    return AxisConfig(
        min = 0f,
        max = axisMax,
        label = "m",
        format = { value ->
            if (value >= 1000f) {
                String.format(java.util.Locale.US, "%.1fkm", value / 1000f)
            } else {
                String.format(java.util.Locale.US, "%.0fm", value)
            }
        }
    )
}


private fun normalizeToScore(seriesType: SeriesType, value: Float): Float {
    return normalizeSeriesBurdenScore(seriesType, value)
}

/**
 * Convert list of RunEvents to ChartEventMarkers with custom color
 */
private fun List<RunEvent>.toChartMarkers(
    runColor: Color,
    distanceMeters: Float?
): List<ChartEventMarker> {
    return mapNotNull { event ->
        val distanceM = distanceFromPct(event.distPct, distanceMeters) ?: return@mapNotNull null
        ChartEventMarker(
            x = distanceM,
            icon = event.type.toMarkerIcon(),
            color = runColor,
            label = event.type.toShortEventLabel()
        )
    }
}

/**
 * Convert a single RunEvent to ChartEventMarker with custom color
 */
private fun RunEvent.toChartMarker(runColor: Color, distanceMeters: Float?): ChartEventMarker? {
    val distanceM = distanceFromPct(distPct, distanceMeters) ?: return null
    return ChartEventMarker(
        x = distanceM,
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

private fun String.toShortEventLabel(): String {
    return when (this) {
        EventType.IMPACT_PEAK -> "IMP"
        EventType.LANDING -> "LAN"
        EventType.HARSHNESS_BURST -> "VIB"
        else -> this.take(3).uppercase(java.util.Locale.US)
    }
}




