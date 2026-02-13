package com.dropindh.app.ui.screens.runsummary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.metrics.formatScore0to100
import com.dropindh.app.ui.metrics.normalizeSeriesBurdenScore
import com.dropindh.app.ui.metrics.runMetricQualityScore
import com.dropindh.app.ui.metrics.runOverallQualityScore
import com.dropindh.app.ui.theme.*
import com.dhmeter.charts.components.ComparisonLineChart
import com.dhmeter.charts.components.EventMarkers
import com.dhmeter.charts.components.HeatmapBar
import com.dhmeter.charts.components.HeatmapColors
import com.dhmeter.charts.model.AxisConfig
import com.dhmeter.charts.model.ChartEventMarker
import com.dhmeter.charts.model.ChartMarkerIcon
import com.dhmeter.charts.model.ChartPoint
import com.dhmeter.charts.model.ChartSeries
import com.dhmeter.charts.model.HeatmapPoint
import com.dhmeter.domain.model.EventType
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSummaryScreen(
    runId: String,
    onCompare: (trackId: String, runAId: String, runBId: String) -> Unit,
    onViewEvents: () -> Unit,
    onViewMap: () -> Unit,
    onBack: () -> Unit,
    viewModel: RunSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCompareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(runId) {
        viewModel.loadRun(runId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = { Text(tr("Run Summary", "Resumen de bajada")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "Atrás"))
                    }
                },
                actions = {
                    uiState.run?.let {
                        IconButton(onClick = onViewMap) {
                            Icon(Icons.Default.Map, contentDescription = tr("Map", "Mapa"))
                        }
                        IconButton(onClick = onViewEvents) {
                            Icon(Icons.Default.List, contentDescription = tr("Events", "Eventos"))
                        }
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
            uiState.run != null -> {
                RunSummaryContent(
                    run = uiState.run!!,
                    comparableRuns = uiState.comparableRuns,
                    impactSeries = uiState.impactSeries,
                    harshnessSeries = uiState.harshnessSeries,
                    stabilitySeries = uiState.stabilitySeries,
                    speedSeries = uiState.speedSeries,
                    events = uiState.events,
                    isChartsLoading = uiState.isChartsLoading,
                    chartsError = uiState.chartsError,
                    onShowCompareDialog = { showCompareDialog = true },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tr("Run not found", "No se encontro la bajada"))
                }
            }
        }
    }

    if (showCompareDialog && uiState.comparableRuns.isNotEmpty()) {
        CompareRunDialog(
            runs = uiState.comparableRuns,
            onDismiss = { showCompareDialog = false },
            onSelect = { selectedRunId ->
                showCompareDialog = false
                onCompare(uiState.run!!.trackId, runId, selectedRunId)
            }
        )
    }
}

@Composable
private fun RunSummaryContent(
    run: Run,
    comparableRuns: List<Run>,
    impactSeries: RunSeries?,
    harshnessSeries: RunSeries?,
    stabilitySeries: RunSeries?,
    speedSeries: RunSeries?,
    events: List<RunEvent>,
    isChartsLoading: Boolean,
    chartsError: String?,
    onShowCompareDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Run info header
        RunInfoHeader(run = run)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Main metrics (shown for all runs, valid or not)
        Text(
            text = tr("Performance Metrics", "Metricas de rendimiento"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        MetricsGrid(run = run)
        
        Spacer(modifier = Modifier.height(24.dp))

        RunChartsSection(
            impactSeries = impactSeries,
            harshnessSeries = harshnessSeries,
            stabilitySeries = stabilitySeries,
            speedSeries = speedSeries,
            distanceMeters = run.distanceMeters,
            avgSpeedMps = run.avgSpeed,
            events = events,
            isLoading = isChartsLoading,
            error = chartsError
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Landing quality section
        run.landingQualityScore?.let { landingScore ->
            LandingQualityCard(score = landingScore)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Compare button (shown for all runs)
        if (comparableRuns.isNotEmpty()) {
            Button(
                onClick = onShowCompareDialog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Compare, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Compare with another run", "Comparar con otra bajada"))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Setup notes
        NotesSection(
            setupNote = run.setupNote,
            conditionsNote = run.conditionsNote
        )
    }
}

@Composable
private fun RunInfoHeader(run: Run) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Run name/date with validity badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormat.format(Date(run.startedAt)),
                style = MaterialTheme.typography.titleMedium
            )
            // GPS quality badge
            AssistChip(
                onClick = {},
                label = { Text(tr("GPS: ${run.gpsQuality}", "GPS: ${run.gpsQuality}")) },
                leadingIcon = {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stats row: Time | Distance | Avg Speed | Pauses
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Duration
                StatItem(
                    label = tr("Time", "Tiempo"),
                    value = formatDuration(run.durationMs)
                )
                
                // Distance
                StatItem(
                    label = tr("Distance", "Distancia"),
                    value = run.distanceMeters?.let { 
                        if (it >= 1000) String.format(Locale.US, "%.2f km", it / 1000f)
                        else String.format(Locale.US, "%.0f m", it)
                    } ?: "--"
                )
                
                // Average speed
                StatItem(
                    label = tr("Avg Speed", "Velocidad prom."),
                    value = run.avgSpeed?.let { 
                        String.format(Locale.US, "%.0f km/h", it * 3.6f) 
                    } ?: "--"
                )
                
                // Pauses
                StatItem(
                    label = tr("Pauses", "Pausas"),
                    value = run.pauseCount.toString()
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun MetricsGrid(run: Run) {
    val impactQuality = runMetricQualityScore(SeriesType.IMPACT_DENSITY, run.impactScore)
    val harshnessQuality = runMetricQualityScore(SeriesType.HARSHNESS, run.harshnessAvg)
    val stabilityQuality = runMetricQualityScore(SeriesType.STABILITY, run.stabilityScore)
    val overallQuality = runOverallQualityScore(run)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QualityOverviewCard(
            overallQuality = overallQuality,
            impactQuality = impactQuality,
            harshnessQuality = harshnessQuality,
            stabilityQuality = stabilityQuality
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                icon = Icons.Default.Bolt,
                label = tr("Impact", "Impacto"),
                score = impactQuality,
                rawValue = run.impactScore?.let { String.format(Locale.US, "%.2f", it) },
                rawLabel = tr("raw density", "densidad base"),
                description = tr("Higher score: smoother impact profile", "Más alto: impacto más suave"),
                color = ChartImpact,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.Vibration,
                label = tr("Harshness", "Vibración"),
                score = harshnessQuality,
                rawValue = run.harshnessAvg?.let { String.format(Locale.US, "%.2f", it) },
                rawLabel = tr("raw RMS", "RMS base"),
                description = tr("Higher score: less vibration", "Más alto: menos vibración"),
                color = ChartHarshness,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Stability (full width - MVP2 has no slope)
        MetricCard(
            icon = Icons.Default.Balance,
            label = tr("Instability", "Inestabilidad"),
            score = stabilityQuality,
            rawValue = run.stabilityScore?.let { String.format(Locale.US, "%.2f", it) },
            rawLabel = tr("raw variance", "varianza base"),
            description = tr("Higher score: steadier control", "Más alto: control más estable"),
            color = ChartStability,
            modifier = Modifier.fillMaxWidth()
        )

        ValueMetricCard(
            icon = Icons.Default.Speed,
            label = tr("Max Speed", "Velocidad máxima"),
            value = run.maxSpeed?.let { String.format(Locale.US, "%.1f km/h", it * 3.6f) } ?: "--",
            description = tr("Highest GPS speed recorded", "Velocidad GPS más alta registrada"),
            color = ChartSpeed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    score: Float?,
    rawValue: String?,
    rawLabel: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatScore0to100(score),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (rawValue != null) {
                Text(
                    text = "$rawLabel: $rawValue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ValueMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
@Composable
private fun QualityOverviewCard(
    overallQuality: Float?,
    impactQuality: Float?,
    harshnessQuality: Float?,
    stabilityQuality: Float?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = tr("Ride Quality Index", "Índice de calidad de bajada"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatScore0to100(overallQuality),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = tr(
                    "Based on impact, harshness and stability (higher is better).",
                    "Basado en impacto, vibración e inestabilidad (más alto es mejor)."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            tr(
                                "Impact ${formatScore0to100(impactQuality)}",
                                "Impacto ${formatScore0to100(impactQuality)}"
                            )
                        )
                    }
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            tr(
                                "Harsh ${formatScore0to100(harshnessQuality)}",
                                "Vibr ${formatScore0to100(harshnessQuality)}"
                            )
                        )
                    }
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            tr(
                                "Instab ${formatScore0to100(stabilityQuality)}",
                                "Inest ${formatScore0to100(stabilityQuality)}"
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LandingQualityCard(score: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FlightLand,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("Landing Quality", "Calidad de aterrizaje"),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(Locale.US, "%.1f", score),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = tr("Lower score = smoother landings", "Menor puntaje = aterrizajes más suaves"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun NotesSection(
    setupNote: String?,
    conditionsNote: String?
) {
    if (setupNote != null || conditionsNote != null) {
        Text(
            text = tr("Notes", "Notas"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        setupNote?.let {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = tr("Setup", "Configuracion"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        conditionsNote?.let {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = tr("Conditions", "Condiciones"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun RunChartsSection(
    impactSeries: RunSeries?,
    harshnessSeries: RunSeries?,
    stabilitySeries: RunSeries?,
    speedSeries: RunSeries?,
    distanceMeters: Float?,
    avgSpeedMps: Float?,
    events: List<RunEvent>,
    isLoading: Boolean,
    error: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = tr("Charts", "Graficas"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = tr(
                "Chart scale is burden score: 0 = smoother/cleaner, 100 = more punishing.",
                "La escala de gráfica es carga: 0 = más suave/limpio, 100 = más castigador."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        when {
            isLoading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = tr("Loading charts...", "Cargando gráficas..."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                SingleRunChartSection(
                    title = tr("Impact Density vs Distance %", "Densidad de impacto vs Distancia %"),
                    series = impactSeries,
                    color = ChartImpact
                )

                SingleRunChartSection(
                    title = tr("Harshness vs Distance %", "Vibración vs Distancia %"),
                    series = harshnessSeries,
                    color = ChartHarshness
                )

                SingleRunChartSection(
                    title = tr("Instability vs Distance %", "Inestabilidad vs Distancia %"),
                    series = stabilitySeries,
                    color = ChartStability
                )

                SpeedChartSection(
                    title = tr("Speed vs Distance %", "Velocidad vs Distancia %"),
                    series = speedSeries,
                    distanceMeters = distanceMeters,
                    fallbackAvgSpeedMps = avgSpeedMps,
                    color = ChartSpeed
                )

                if (events.isNotEmpty()) {
                    Text(
                        text = tr("Events over Distance %", "Eventos sobre Distancia %"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    EventMarkers(
                        markers = events.toChartMarkers(),
                        xMin = 0f,
                        xMax = 100f,
                        showLabels = true
                    )
                }

                val speedHeatmapPoints = speedSeries
                    ?.toSpeedHeatmapPoints(distanceMeters)
                    .orEmpty()
                    .ifEmpty { fallbackSpeedHeatmapPoints(avgSpeedMps) }
                if (speedHeatmapPoints.isNotEmpty()) {
                    val maxSpeed = speedHeatmapPoints.maxOfOrNull { it.value } ?: 0f
                    Text(
                        text = tr("Speed Heatmap", "Mapa de calor de velocidad"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    HeatmapBar(
                        points = speedHeatmapPoints,
                        colors = HeatmapColors.Speed,
                        minValue = 0f,
                        maxValue = maxSpeed.coerceAtLeast(10f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleRunChartSection(
    title: String,
    series: RunSeries?,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        val points = series?.toChartPoints().orEmpty()
        if (points.isEmpty()) {
            Text(
                text = tr("No data available", "No hay datos disponibles"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ComparisonLineChart(
                series = listOf(
                    ChartSeries(
                        label = tr("Run", "Bajada"),
                        points = points,
                        color = color
                    )
                ),
                xAxisConfig = AxisConfig(0f, 100f, label = tr("Distance %", "Distancia %")),
                yAxisConfig = AxisConfig(0f, 100f, label = tr("Score (0-100)", "Puntaje (0-100)")),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            val peakPoint = points.maxByOrNull { it.y }
            peakPoint?.let {
                Text(
                    text = tr(
                        "Peak burden ${String.format(Locale.US, "%.0f", it.y)} at ${String.format(Locale.US, "%.0f", it.x)}% of run",
                        "Pico de carga ${String.format(Locale.US, "%.0f", it.y)} en ${String.format(Locale.US, "%.0f", it.x)}% de la bajada"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SpeedChartSection(
    title: String,
    series: RunSeries?,
    distanceMeters: Float?,
    fallbackAvgSpeedMps: Float?,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        val points = series
            ?.toSpeedChartPoints(distanceMeters)
            .orEmpty()
            .ifEmpty { fallbackSpeedChartPoints(fallbackAvgSpeedMps) }
        if (points.isEmpty()) {
            Text(
                text = tr("No data available", "No hay datos disponibles"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxSpeed = points.maxOfOrNull { it.y } ?: 0f
            val axisMax = (kotlin.math.ceil(maxSpeed / 5f) * 5f).coerceAtLeast(10f)

            ComparisonLineChart(
                series = listOf(
                    ChartSeries(
                        label = tr("Speed", "Velocidad"),
                        points = points,
                        color = color
                    )
                ),
                xAxisConfig = AxisConfig(0f, 100f, label = tr("Distance %", "Distancia %")),
                yAxisConfig = AxisConfig(0f, axisMax, label = "km/h"),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

private fun RunSeries.toChartPoints(): List<ChartPoint> {
    val count = effectivePointCount
    return (0 until count).mapNotNull { i ->
        ChartPoint(points[i * 2], normalizeToScore(seriesType, points[i * 2 + 1]))
            .takeIf { it.x.isFinite() && it.y.isFinite() }
    }
}

private fun RunSeries.toSpeedChartPoints(distanceMeters: Float?): List<ChartPoint> {
    if (seriesType != SeriesType.SPEED_TIME) return emptyList()
    val totalDistanceM = distanceMeters?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val count = effectivePointCount
    if (count < 2) return emptyList()

    val result = ArrayList<ChartPoint>(count - 1)
    for (i in 1 until count) {
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

private fun RunSeries.toSpeedHeatmapPoints(distanceMeters: Float?): List<HeatmapPoint> {
    return toSpeedChartPoints(distanceMeters)
        .map { HeatmapPoint(x = it.x, value = it.y.coerceAtLeast(0f)) }
}

private fun fallbackSpeedHeatmapPoints(avgSpeedMps: Float?): List<HeatmapPoint> {
    val speedMps = avgSpeedMps?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val speedKmh = speedMps * 3.6f
    return listOf(
        HeatmapPoint(0f, speedKmh),
        HeatmapPoint(100f, speedKmh)
    )
}

private fun normalizeToScore(seriesType: SeriesType, value: Float): Float {
    return normalizeSeriesBurdenScore(seriesType, value)
}

private fun List<RunEvent>.toChartMarkers(): List<ChartEventMarker> {
    return map { event ->
        ChartEventMarker(
            x = event.distPct,
            icon = event.type.toMarkerIcon(),
            color = event.type.toMarkerColor(),
            label = event.type.shortLabel()
        )
    }
}

private fun String.toMarkerIcon(): ChartMarkerIcon {
    return when (this) {
        EventType.IMPACT_PEAK -> ChartMarkerIcon.TRIANGLE
        EventType.LANDING -> ChartMarkerIcon.DIAMOND
        EventType.HARSHNESS_BURST -> ChartMarkerIcon.SQUARE
        else -> ChartMarkerIcon.CIRCLE
    }
}

private fun String.toMarkerColor(): Color {
    return when (this) {
        EventType.IMPACT_PEAK -> Color(0xFFF44336)
        EventType.LANDING -> Color(0xFFFF9800)
        EventType.HARSHNESS_BURST -> Color(0xFFFFEB3B)
        else -> Color(0xFF9E9E9E)
    }
}

private fun String.shortLabel(): String {
    return when (this) {
        EventType.IMPACT_PEAK -> "IMP"
        EventType.LANDING -> "LDG"
        EventType.HARSHNESS_BURST -> "HRS"
        else -> "EVT"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareRunDialog(
    runs: List<Run>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Select run to compare", "Selecciona bajada para comparar")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                runs.forEach { run ->
                    ListItem(
                        headlineContent = { 
                            Text(dateFormat.format(Date(run.startedAt)))
                        },
                        supportingContent = { 
                            Text(tr("Duration: ${formatDuration(run.durationMs)}", "Duración: ${formatDuration(run.durationMs)}"))
                        },
                        modifier = Modifier.clickable { onSelect(run.runId) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "Cancelar"))
            }
        }
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}



