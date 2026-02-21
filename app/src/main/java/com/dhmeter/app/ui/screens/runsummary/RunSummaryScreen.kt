package com.dropindh.app.ui.screens.runsummary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.dropindh.app.BuildConfig
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.metrics.formatScore0to100
import com.dropindh.app.ui.metrics.normalizeSeriesBurdenScore
import com.dropindh.app.ui.metrics.runMetricBurdenScore
import com.dropindh.app.ui.metrics.runOverallBurdenScore
import com.dropindh.app.ui.theme.*
import com.dhmeter.charts.components.ComparisonLineChart
import com.dhmeter.charts.components.HeatmapBar
import com.dhmeter.charts.components.HeatmapColors
import com.dhmeter.charts.model.AxisConfig
import com.dhmeter.charts.model.ChartPoint
import com.dhmeter.charts.model.ChartSeries
import com.dhmeter.charts.model.HeatmapPoint
import com.dhmeter.domain.model.ElevationProfile
import com.dhmeter.domain.model.EventType
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunMapData
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SegmentSeverity
import com.dhmeter.domain.model.SeriesType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSummaryScreen(
    runId: String,
    onCompare: (trackId: String, runAId: String, runBId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: RunSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCompareDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(runId) {
        viewModel.loadRun(runId)
    }

    LaunchedEffect(uiState.exportMessage) {
        val message = uiState.exportMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeExportMessage()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = { Text(tr("Run Summary", "Resumen de bajada")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Atrás"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.exportRunDiagnosticsJson() },
                        enabled = uiState.run != null && !uiState.isExportingDiagnostics
                    ) {
                        if (uiState.isExportingDiagnostics) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = tr(
                                    "Export diagnostics JSON",
                                    "Exportar JSON de diagnóstico"
                                )
                            )
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
                    mapData = uiState.mapData,
                    elevationProfile = uiState.elevationProfile,
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
                    Text(tr("Run not found", "No se encontró la bajada"))
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
    mapData: RunMapData?,
    elevationProfile: ElevationProfile?,
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
            elevationProfile = elevationProfile,
            distanceMeters = run.distanceMeters,
            avgSpeedMps = run.avgSpeed,
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

        Spacer(modifier = Modifier.height(24.dp))

        RunMapAndEventsSection(
            mapData = mapData,
            events = events,
            speedSeries = speedSeries,
            runDistanceMeters = run.distanceMeters,
            avgSpeedMps = run.avgSpeed
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
    val impactBurden = runMetricBurdenScore(SeriesType.IMPACT_DENSITY, run.impactScore)
    val harshnessBurden = runMetricBurdenScore(SeriesType.HARSHNESS, run.harshnessAvg)
    val stabilityBurden = runMetricBurdenScore(SeriesType.STABILITY, run.stabilityScore)
    val overallBurden = runOverallBurdenScore(run)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BurdenOverviewCard(
            overallBurden = overallBurden,
            impactBurden = impactBurden,
            harshnessBurden = harshnessBurden,
            stabilityBurden = stabilityBurden
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                icon = Icons.Default.Bolt,
                label = tr("Impact", "Impacto"),
                score = impactBurden,
                rawValue = run.impactScore?.let { String.format(Locale.US, "%.2f", it) },
                rawLabel = tr("raw density", "densidad base"),
                description = tr("Higher score: more impact density", "Más alto: mayor densidad de impacto"),
                color = ChartImpact,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.Vibration,
                label = tr("Harshness", "Vibración"),
                score = harshnessBurden,
                rawValue = run.harshnessAvg?.let { String.format(Locale.US, "%.2f", it) },
                rawLabel = tr("raw RMS", "RMS base"),
                description = tr("Higher score: more vibration", "Más alto: mayor vibración"),
                color = ChartHarshness,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Stability (full width - MVP2 has no slope)
        MetricCard(
            icon = Icons.Default.Balance,
            label = tr("Instability", "Inestabilidad"),
            score = stabilityBurden,
            rawValue = run.stabilityScore?.let { String.format(Locale.US, "%.2f", it) },
            rawLabel = tr("raw variance", "varianza base"),
            description = tr("Higher score: more instability", "Más alto: mayor inestabilidad"),
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
private fun BurdenOverviewCard(
    overallBurden: Float?,
    impactBurden: Float?,
    harshnessBurden: Float?,
    stabilityBurden: Float?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = tr("Ride Severity Index", "Índice de severidad de bajada"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatScore0to100(overallBurden),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = tr(
                    "Based on impact, harshness and instability (higher is more punishing).",
                    "Basado en impacto, vibración e inestabilidad (más alto es más castigador)."
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
                                "Impact ${formatScore0to100(impactBurden)}",
                                "Impacto ${formatScore0to100(impactBurden)}"
                            )
                        )
                    }
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            tr(
                                "Harsh ${formatScore0to100(harshnessBurden)}",
                                "Vibr ${formatScore0to100(harshnessBurden)}"
                            )
                        )
                    }
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            tr(
                                "Instab ${formatScore0to100(stabilityBurden)}",
                                "Inest ${formatScore0to100(stabilityBurden)}"
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
    elevationProfile: ElevationProfile?,
    distanceMeters: Float?,
    avgSpeedMps: Float?,
    isLoading: Boolean,
    error: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = tr("Charts", "Gráficas"),
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

                AltitudeChartSection(
                    title = tr("Altitude vs Distance %", "Altitud vs Distancia %"),
                    profile = elevationProfile,
                    color = ChartSpeed
                )

                val speedHeatmapPoints = remember(speedSeries, distanceMeters, avgSpeedMps) {
                    speedSeries
                        ?.toSpeedHeatmapPoints(distanceMeters)
                        .orEmpty()
                        .ifEmpty { fallbackSpeedHeatmapPoints(avgSpeedMps) }
                }
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

        val points = remember(series) { series?.toChartPoints().orEmpty() }
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

        val points = remember(series, distanceMeters, fallbackAvgSpeedMps) {
            series
                ?.toSpeedChartPoints(distanceMeters)
                .orEmpty()
                .ifEmpty { fallbackSpeedChartPoints(fallbackAvgSpeedMps) }
        }
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

@Composable
private fun AltitudeChartSection(
    title: String,
    profile: ElevationProfile?,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        val points = remember(profile) {
            profile?.points
                ?.filter { it.distPct.isFinite() && it.altitudeM.isFinite() }
                ?.sortedBy { it.distPct }
                ?.map { ChartPoint(it.distPct.coerceIn(0f, 100f), it.altitudeM) }
                .orEmpty()
        }

        if (points.size < 2) {
            Text(
                text = tr("No altitude data available", "No hay datos de altitud disponibles"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val minAltitude = points.minOf { it.y }
            val maxAltitude = points.maxOf { it.y }
            val range = (maxAltitude - minAltitude).coerceAtLeast(1f)
            val padding = (range * 0.08f).coerceAtLeast(5f)
            val axisMin = (minAltitude - padding).coerceAtMost(minAltitude)
            val axisMax = (maxAltitude + padding).coerceAtLeast(maxAltitude + 1f)

            ComparisonLineChart(
                series = listOf(
                    ChartSeries(
                        label = tr("Altitude", "Altitud"),
                        points = points,
                        color = color
                    )
                ),
                xAxisConfig = AxisConfig(0f, 100f, label = tr("Distance %", "Distancia %")),
                yAxisConfig = AxisConfig(axisMin, axisMax, label = tr("Altitude (m)", "Altitud (m)")),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            profile?.let {
                Text(
                    text = tr(
                        "Descent ${formatMeters(it.totalDescentM)} | Ascent ${formatMeters(it.totalAscentM)}",
                        "Bajada ${formatMeters(it.totalDescentM)} | Subida ${formatMeters(it.totalAscentM)}"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun RunMapAndEventsSection(
    mapData: RunMapData?,
    events: List<RunEvent>,
    speedSeries: RunSeries?,
    runDistanceMeters: Float?,
    avgSpeedMps: Float?
) {
    val sortedEvents = remember(events) { events.sortedBy { it.distPct } }
    var selectedEventId by remember(events) { mutableStateOf<String?>(null) }

    Text(
        text = tr("Map & Events", "Mapa y eventos"),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))

    val points = mapData?.polyline?.points.orEmpty()
    if (mapData == null || points.size < 2) {
        Text(
            text = tr("No map data available", "No hay datos de mapa disponibles"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (!BuildConfig.HAS_MAPS_API_KEY) {
        Text(
            text = tr(
                "Google Maps API key is missing. Configure MAPS_API_KEY to render the map.",
                "Falta la API key de Google Maps. Configura MAPS_API_KEY para renderizar el mapa."
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    } else {
        val startPoint = points.firstOrNull()
        val endPoint = points.lastOrNull()
        val selectedEvent = sortedEvents.firstOrNull { it.eventId == selectedEventId }
        val selectedEventPoint = selectedEvent?.let {
            findLatLngForDistPct(points, it.distPct)
        }
        var mapLoaded by remember(points) { mutableStateOf(false) }
        val boundsBuilder = remember(points) {
            LatLngBounds.builder().apply {
                points.forEach { include(LatLng(it.lat, it.lon)) }
            }
        }
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                LatLng(points.first().lat, points.first().lon),
                15f
            )
        }

        LaunchedEffect(boundsBuilder, mapLoaded) {
            if (!mapLoaded) return@LaunchedEffect
            runCatching {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100)
                )
            }
        }
        LaunchedEffect(selectedEventPoint, mapLoaded) {
            val marker = selectedEventPoint ?: return@LaunchedEffect
            if (!mapLoaded) return@LaunchedEffect
            runCatching {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(marker, 17f)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = MapType.HYBRID,
                    isTrafficEnabled = false
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = false
                ),
                onMapLoaded = { mapLoaded = true }
            ) {
                Polyline(
                    points = points.map { LatLng(it.lat, it.lon) },
                    color = Color(0xFF607D8B),
                    width = 4f
                )

                mapData.segments.forEach { segment ->
                    Polyline(
                        points = listOf(
                            LatLng(segment.start.lat, segment.start.lon),
                            LatLng(segment.end.lat, segment.end.lon)
                        ),
                        color = segment.severity.toMapColor(),
                        width = 10f
                    )
                }

                startPoint?.let { start ->
                    com.google.maps.android.compose.Circle(
                        center = LatLng(start.lat, start.lon),
                        radius = 2.8,
                        fillColor = Color(0xFF4FC3F7),
                        strokeColor = Color.White,
                        strokeWidth = 2f
                    )
                }

                endPoint?.let { end ->
                    com.google.maps.android.compose.Circle(
                        center = LatLng(end.lat, end.lon),
                        radius = 2.8,
                        fillColor = Color(0xFF66BB6A),
                        strokeColor = Color.White,
                        strokeWidth = 2f
                    )
                }

                selectedEvent?.let { selected ->
                    selectedEventPoint?.let { marker ->
                        com.google.maps.android.compose.Circle(
                            center = marker,
                            radius = 5.2,
                            fillColor = Color.White.copy(alpha = 0.9f),
                            strokeColor = Color.White,
                            strokeWidth = 2f
                        )
                        com.google.maps.android.compose.Circle(
                            center = marker,
                            radius = 2.6,
                            fillColor = eventTypeColor(selected.type),
                            strokeColor = Color.Black.copy(alpha = 0.35f),
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = tr("Event list", "Lista de eventos"),
        style = MaterialTheme.typography.titleSmall
    )

    if (events.isEmpty()) {
        Text(
            text = tr("No events detected in this run.", "No se detectaron eventos en esta bajada."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sortedEvents.forEach { event ->
                val eventName = eventTypeLabel(event.type)
                val eventIcon = when (event.type) {
                    EventType.IMPACT_PEAK -> Icons.Default.Bolt
                    EventType.LANDING -> Icons.Default.FlightLand
                    EventType.HARSHNESS_BURST -> Icons.Default.Vibration
                    else -> Icons.Default.Info
                }
                val eventColor = eventTypeColor(event.type)
                val isSelected = selectedEventId == event.eventId
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedEventId = if (isSelected) null else event.eventId
                        },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) eventColor else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = eventIcon,
                            contentDescription = null,
                            tint = eventColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = eventName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = tr(
                                    "Code ${eventTypeShortCode(event.type)}",
                                    "Sigla ${eventTypeShortCode(event.type)}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = eventColor
                            )
                            Text(
                                text = tr(
                                    "Dist ${String.format(Locale.US, "%.1f", event.distPct)}% | Time ${String.format(Locale.US, "%.1f", event.timeSec)} s | Sev ${String.format(Locale.US, "%.2f", event.severity)}",
                                    "Dist ${String.format(Locale.US, "%.1f", event.distPct)}% | Tiempo ${String.format(Locale.US, "%.1f", event.timeSec)} s | Sev ${String.format(Locale.US, "%.2f", event.severity)}"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val speedHeatmapPoints = remember(speedSeries, runDistanceMeters, avgSpeedMps) {
                speedSeries
                    ?.toSpeedHeatmapPoints(runDistanceMeters)
                    .orEmpty()
                    .ifEmpty { fallbackSpeedHeatmapPoints(avgSpeedMps) }
            }
            val correlationStats = remember(sortedEvents, speedHeatmapPoints) {
                buildEventSpeedCorrelationStats(sortedEvents, speedHeatmapPoints)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tr(
                    "Event-speed heatmap correlation",
                    "Correlación eventos - mapa de calor de velocidad"
                ),
                style = MaterialTheme.typography.titleSmall
            )
            if (correlationStats == null) {
                Text(
                    text = tr(
                        "Not enough data to build a correlation summary.",
                        "No hay suficientes datos para generar el análisis de correlación."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val trendText = when {
                    correlationStats.highSpeedEvents > correlationStats.lowSpeedEvents + 1 -> tr(
                        "Most events occur in faster sections of the speed heatmap.",
                        "La mayoría de eventos ocurre en secciones rápidas del mapa de calor."
                    )

                    correlationStats.lowSpeedEvents > correlationStats.highSpeedEvents + 1 -> tr(
                        "Most events occur in slower sections of the speed heatmap.",
                        "La mayoría de eventos ocurre en secciones lentas del mapa de calor."
                    )

                    else -> tr(
                        "Events are distributed between fast and slow sections.",
                        "Los eventos están distribuidos entre secciones rápidas y lentas."
                    )
                }

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trendText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = tr(
                                "Mapped ${correlationStats.mappedEvents}/${correlationStats.totalEvents} events. Event avg ${String.format(Locale.US, "%.1f", correlationStats.avgEventSpeedKmh)} km/h vs run heatmap avg ${String.format(Locale.US, "%.1f", correlationStats.avgRunSpeedKmh)} km/h.",
                                "Se mapearon ${correlationStats.mappedEvents}/${correlationStats.totalEvents} eventos. Promedio de velocidad en eventos ${String.format(Locale.US, "%.1f", correlationStats.avgEventSpeedKmh)} km/h vs promedio de heatmap ${String.format(Locale.US, "%.1f", correlationStats.avgRunSpeedKmh)} km/h."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tr(
                                "Low-speed zone <= ${String.format(Locale.US, "%.1f", correlationStats.lowSpeedThresholdKmh)} km/h | High-speed zone >= ${String.format(Locale.US, "%.1f", correlationStats.highSpeedThresholdKmh)} km/h.",
                                "Zona lenta <= ${String.format(Locale.US, "%.1f", correlationStats.lowSpeedThresholdKmh)} km/h | Zona rápida >= ${String.format(Locale.US, "%.1f", correlationStats.highSpeedThresholdKmh)} km/h."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        correlationStats.mostFrequentEventCode?.let { code ->
                            Text(
                                text = tr(
                                    "Most frequent event type: $code",
                                    "Tipo de evento más frecuente: $code"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        correlationStats.mostFrequentHighSpeedEventCode?.let { code ->
                            Text(
                                text = tr(
                                    "Most frequent event in high-speed zones: $code",
                                    "Evento más frecuente en zonas rápidas: $code"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class EventSpeedCorrelationStats(
    val totalEvents: Int,
    val mappedEvents: Int,
    val lowSpeedEvents: Int,
    val highSpeedEvents: Int,
    val avgEventSpeedKmh: Float,
    val avgRunSpeedKmh: Float,
    val lowSpeedThresholdKmh: Float,
    val highSpeedThresholdKmh: Float,
    val mostFrequentEventCode: String?,
    val mostFrequentHighSpeedEventCode: String?
)

private fun buildEventSpeedCorrelationStats(
    events: List<RunEvent>,
    speedPoints: List<HeatmapPoint>
): EventSpeedCorrelationStats? {
    if (events.isEmpty() || speedPoints.isEmpty()) return null
    val cleanSpeedPoints = speedPoints.filter { it.x.isFinite() && it.value.isFinite() }
    if (cleanSpeedPoints.isEmpty()) return null

    val speedValues = cleanSpeedPoints.map { it.value }.sorted()
    if (speedValues.isEmpty()) return null

    val lowThreshold = percentile(speedValues, 0.25f)
    val highThreshold = percentile(speedValues, 0.75f)

    val eventSpeeds = events.mapNotNull { event ->
        nearestSpeedAtDistPct(cleanSpeedPoints, event.distPct)?.let { speed ->
            event to speed
        }
    }
    if (eventSpeeds.isEmpty()) return null

    val lowCount = eventSpeeds.count { (_, speed) -> speed <= lowThreshold }
    val highCount = eventSpeeds.count { (_, speed) -> speed >= highThreshold }
    val avgEventSpeed = eventSpeeds.map { it.second }.average().toFloat()
    val avgRunSpeed = speedValues.average().toFloat()

    val mostFrequent = eventSpeeds
        .groupingBy { (event, _) -> event.type }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?.let(::eventTypeShortCode)

    val mostFrequentHighSpeed = eventSpeeds
        .filter { (_, speed) -> speed >= highThreshold }
        .groupingBy { (event, _) -> event.type }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?.let(::eventTypeShortCode)

    return EventSpeedCorrelationStats(
        totalEvents = events.size,
        mappedEvents = eventSpeeds.size,
        lowSpeedEvents = lowCount,
        highSpeedEvents = highCount,
        avgEventSpeedKmh = avgEventSpeed,
        avgRunSpeedKmh = avgRunSpeed,
        lowSpeedThresholdKmh = lowThreshold,
        highSpeedThresholdKmh = highThreshold,
        mostFrequentEventCode = mostFrequent,
        mostFrequentHighSpeedEventCode = mostFrequentHighSpeed
    )
}

private fun findLatLngForDistPct(
    points: List<com.dhmeter.domain.model.GpsPoint>,
    distPct: Float
): LatLng? {
    if (points.isEmpty()) return null
    val target = distPct.coerceIn(0f, 100f)
    val nearest = points.minByOrNull { point ->
        kotlin.math.abs(point.distPct - target)
    } ?: return null
    return LatLng(nearest.lat, nearest.lon)
}

private fun nearestSpeedAtDistPct(speedPoints: List<HeatmapPoint>, distPct: Float): Float? {
    if (speedPoints.isEmpty()) return null
    val target = distPct.coerceIn(0f, 100f)
    return speedPoints.minByOrNull { point ->
        kotlin.math.abs(point.x - target)
    }?.value
}

private fun percentile(sortedValues: List<Float>, fraction: Float): Float {
    if (sortedValues.isEmpty()) return 0f
    if (sortedValues.size == 1) return sortedValues.first()
    val clamped = fraction.coerceIn(0f, 1f)
    val index = (clamped * (sortedValues.lastIndex)).toInt().coerceIn(0, sortedValues.lastIndex)
    return sortedValues[index]
}

private fun eventTypeShortCode(type: String): String {
    return when (type) {
        EventType.IMPACT_PEAK -> "IMP"
        EventType.LANDING -> "LAN"
        EventType.HARSHNESS_BURST -> "HAR"
        else -> type.take(3).uppercase(Locale.US)
    }
}

@Composable
private fun eventTypeLabel(type: String): String {
    return when (type) {
        EventType.IMPACT_PEAK -> tr("Strong Impact", "Impacto fuerte")
        EventType.LANDING -> tr("Hard Landing", "Aterrizaje duro")
        EventType.HARSHNESS_BURST -> tr("Harshness Burst", "Ráfaga de vibración")
        else -> tr("Event", "Evento")
    }
}

private fun eventTypeColor(type: String): Color {
    return when (type) {
        EventType.IMPACT_PEAK -> ChartImpact
        EventType.LANDING -> Color(0xFFFF9800)
        EventType.HARSHNESS_BURST -> YellowWarning
        else -> Color(0xFF90CAF9)
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

private fun SegmentSeverity.toMapColor(): Color {
    return when (this) {
        SegmentSeverity.VERY_LOW -> Color(0xFF1B5E20)
        SegmentSeverity.LOW -> Color(0xFF4CAF50)
        SegmentSeverity.MEDIUM -> Color(0xFFFFC107)
        SegmentSeverity.HIGH -> Color(0xFFFF9800)
        SegmentSeverity.VERY_HIGH -> Color(0xFFD32F2F)
    }
}

private fun formatMeters(value: Float): String {
    return String.format(Locale.US, "%.1f m", value)
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
