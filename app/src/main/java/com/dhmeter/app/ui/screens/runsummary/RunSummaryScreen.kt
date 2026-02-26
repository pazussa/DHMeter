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
import com.dropindh.app.ui.charts.distanceFromPct
import com.dropindh.app.ui.charts.pctFromDistance
import com.dropindh.app.ui.charts.toAccelerationChartPointsMeters
import com.dropindh.app.ui.charts.toBurdenChartPointsMeters
import com.dropindh.app.ui.charts.toSpeedChartPointsMeters
import com.dropindh.app.ui.charts.toSpeedHeatmapPointsMeters
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
    var selectedDistanceM by remember(run.runId) { mutableStateOf<Float?>(null) }

    val speedChartPoints = remember(speedSeries, run.distanceMeters, run.avgSpeed) {
        speedSeries
            ?.toSpeedChartPointsMeters(run.distanceMeters)
            .orEmpty()
            .ifEmpty { fallbackSpeedChartPoints(run.avgSpeed, run.distanceMeters) }
    }
    val accelerationChartPoints = remember(speedSeries, run.distanceMeters) {
        speedSeries?.toAccelerationChartPointsMeters(run.distanceMeters).orEmpty()
    }
    val dynamicsInsights = remember(events, speedChartPoints, accelerationChartPoints, run.distanceMeters) {
        buildRunDynamicsInsights(
            events = events.sortedBy { it.distPct },
            speedPoints = speedChartPoints,
            accelerationPoints = accelerationChartPoints,
            totalDistanceM = run.distanceMeters
        )
    }

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

        run.landingQualityScore?.let { landingScore ->
            LandingQualityCard(score = landingScore)
            Spacer(modifier = Modifier.height(24.dp))
        }

        RunMapSection(
            mapData = mapData,
            runDistanceMeters = run.distanceMeters,
            selectedDistanceM = selectedDistanceM
        )

        Spacer(modifier = Modifier.height(24.dp))

        RunChartsCarouselSection(
            impactSeries = impactSeries,
            harshnessSeries = harshnessSeries,
            stabilitySeries = stabilitySeries,
            speedSeries = speedSeries,
            elevationProfile = elevationProfile,
            distanceMeters = run.distanceMeters,
            avgSpeedMps = run.avgSpeed,
            isLoading = isChartsLoading,
            error = chartsError,
            onDistanceSelected = { selectedDistance ->
                selectedDistanceM = selectedDistance
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        RunDynamicsAnalysisSection(
            dynamicsInsights = dynamicsInsights,
            mapData = mapData,
            runDistanceMeters = run.distanceMeters,
            selectedDistanceM = selectedDistanceM,
            events = events,
            speedPoints = speedChartPoints,
            accelerationPoints = accelerationChartPoints,
            onDistanceSelected = { selectedDistance ->
                selectedDistanceM = selectedDistance
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (comparableRuns.isNotEmpty()) {
            Button(
                onClick = onShowCompareDialog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Compare, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Compare with another run", "Comparar con otra bajada"))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        NotesSection(
            setupNote = run.setupNote,
            conditionsNote = run.conditionsNote
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = tr(
                "Repeat the run for a more complete analysis by comparing sections.",
                "Repite la bajada para un analisis mas completo comparando entre secciones."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
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
private fun RunChartsCarouselSection(
    impactSeries: RunSeries?,
    harshnessSeries: RunSeries?,
    stabilitySeries: RunSeries?,
    speedSeries: RunSeries?,
    elevationProfile: ElevationProfile?,
    distanceMeters: Float?,
    avgSpeedMps: Float?,
    isLoading: Boolean,
    error: String?,
    onDistanceSelected: (Float) -> Unit
) {
    var currentChartIndex by remember { mutableIntStateOf(0) }
    val chartTitles = listOf(
        tr("Impacts", "Impactos"),
        tr("Vibration", "Vibracion"),
        tr("Instability", "Inestabilidad"),
        tr("Speed", "Velocidad"),
        tr("Acceleration", "Aceleracion"),
        tr("Altitude", "Altitud")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = tr("Run overview", "Resumen visual de la bajada"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = tr(
                "Tap any point to see where it happened on the trail.",
                "Toca cualquier punto para ver donde paso en el sendero."
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
                currentChartIndex = currentChartIndex.coerceIn(0, chartTitles.lastIndex)

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    currentChartIndex =
                                        if (currentChartIndex == 0) chartTitles.lastIndex else currentChartIndex - 1
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = tr("Previous chart", "Gráfica anterior"))
                            }

                            Text(
                                text = tr(
                                    "${chartTitles[currentChartIndex]} (${currentChartIndex + 1}/${chartTitles.size})",
                                    "${chartTitles[currentChartIndex]} (${currentChartIndex + 1}/${chartTitles.size})"
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center
                            )

                            IconButton(
                                onClick = {
                                    currentChartIndex =
                                        if (currentChartIndex == chartTitles.lastIndex) 0 else currentChartIndex + 1
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = tr("Next chart", "Siguiente gráfica"))
                            }
                        }

                        when (currentChartIndex) {
                            0 -> SingleRunChartSection(
                                title = tr("Impacts vs Distance (m)", "Impactos vs Distancia (m)"),
                                series = impactSeries,
                                distanceMeters = distanceMeters,
                                color = ChartImpact,
                                onDistanceSelected = onDistanceSelected
                            )
                            1 -> SingleRunChartSection(
                                title = tr("Harshness vs Distance (m)", "Vibración vs Distancia (m)"),
                                series = harshnessSeries,
                                distanceMeters = distanceMeters,
                                color = ChartHarshness,
                                onDistanceSelected = onDistanceSelected
                            )
                            2 -> SingleRunChartSection(
                                title = tr("Instability vs Distance (m)", "Inestabilidad vs Distancia (m)"),
                                series = stabilitySeries,
                                distanceMeters = distanceMeters,
                                color = ChartStability,
                                onDistanceSelected = onDistanceSelected
                            )
                            3 -> SpeedChartSection(
                                title = tr("Speed vs Distance (m)", "Velocidad vs Distancia (m)"),
                                series = speedSeries,
                                distanceMeters = distanceMeters,
                                fallbackAvgSpeedMps = avgSpeedMps,
                                color = ChartSpeed,
                                onDistanceSelected = onDistanceSelected
                            )
                            4 -> AccelerationChartSection(
                                title = tr("Acceleration vs Distance (m)", "Aceleracion vs Distancia (m)"),
                                series = speedSeries,
                                distanceMeters = distanceMeters,
                                color = ChartStability,
                                onDistanceSelected = onDistanceSelected
                            )
                            else -> AltitudeChartSection(
                                title = tr("Altitude vs Distance (m)", "Altitud vs Distancia (m)"),
                                profile = elevationProfile,
                                distanceMeters = distanceMeters,
                                color = ChartSpeed,
                                onDistanceSelected = onDistanceSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleRunChartSection(
    title: String,
    series: RunSeries?,
    distanceMeters: Float?,
    color: Color,
    onDistanceSelected: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        val points = remember(series, distanceMeters) { series?.toChartPoints(distanceMeters).orEmpty() }
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
                xAxisConfig = meterAxisConfig(points),
                yAxisConfig = AxisConfig(0f, 100f, label = tr("Score (0-100)", "Puntaje (0-100)")),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onPointSelected = { _, _, xValue, _ ->
                    onDistanceSelected(xValue)
                }
            )

            val peakPoint = points.maxByOrNull { it.y }
            peakPoint?.let {
                Text(
                    text = tr(
                        "Most demanding point around ${formatDistanceLabel(it.x)}",
                        "Punto mas exigente cerca de ${formatDistanceLabel(it.x)}"
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
    color: Color,
    onDistanceSelected: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        val points = remember(series, distanceMeters, fallbackAvgSpeedMps) {
            series
                ?.toSpeedChartPointsMeters(distanceMeters)
                .orEmpty()
                .ifEmpty { fallbackSpeedChartPoints(fallbackAvgSpeedMps, distanceMeters) }
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
                xAxisConfig = meterAxisConfig(points),
                yAxisConfig = AxisConfig(0f, axisMax, label = "km/h"),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onPointSelected = { _, _, xValue, _ ->
                    onDistanceSelected(xValue)
                }
            )
        }
    }
}

@Composable
private fun AccelerationChartSection(
    title: String,
    series: RunSeries?,
    distanceMeters: Float?,
    color: Color,
    onDistanceSelected: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = tr(
                "Shows where you brake hard or recover speed.",
                "Muestra donde frenas fuerte o recuperas velocidad."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        val points = remember(series, distanceMeters) {
            series?.toAccelerationChartPointsMeters(distanceMeters).orEmpty()
        }
        if (points.isEmpty()) {
            Text(
                text = tr("No data available", "No hay datos disponibles"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxAbs = points.maxOfOrNull { kotlin.math.abs(it.y) } ?: 0f
            val axisLimit = (kotlin.math.ceil(maxAbs * 2f) / 2f).coerceAtLeast(0.5f)

            ComparisonLineChart(
                series = listOf(
                    ChartSeries(
                        label = tr("Acceleration", "Aceleracion"),
                        points = points,
                        color = color
                    )
                ),
                xAxisConfig = meterAxisConfig(points),
                yAxisConfig = AxisConfig(-axisLimit, axisLimit, label = "m/s²"),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onPointSelected = { _, _, xValue, _ ->
                    onDistanceSelected(xValue)
                }
            )
        }
    }
}

@Composable
private fun AltitudeChartSection(
    title: String,
    profile: ElevationProfile?,
    distanceMeters: Float?,
    color: Color,
    onDistanceSelected: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )

        val points = remember(profile, distanceMeters) {
            val totalDistance = distanceMeters?.takeIf { it.isFinite() && it > 0f } ?: return@remember emptyList()
            profile?.points
                ?.filter { it.distPct.isFinite() && it.altitudeM.isFinite() }
                ?.sortedBy { it.distPct }
                ?.map { point ->
                    ChartPoint(
                        x = (totalDistance * (point.distPct.coerceIn(0f, 100f) / 100f)).coerceIn(0f, totalDistance),
                        y = point.altitudeM
                    )
                }
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
                xAxisConfig = meterAxisConfig(points),
                yAxisConfig = AxisConfig(axisMin, axisMax, label = tr("Altitude (m)", "Altitud (m)")),
                showLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onPointSelected = { _, _, xValue, _ ->
                    onDistanceSelected(xValue)
                }
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
private fun RunMapSection(
    mapData: RunMapData?,
    runDistanceMeters: Float?,
    selectedDistanceM: Float?
) {
    Text(
        text = tr("Map", "Mapa"),
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
        val orderedPoints = remember(points) { points.sortedBy { it.distPct } }
        val totalDistanceM = runDistanceMeters?.takeIf { it.isFinite() && it > 0f }
            ?: mapData.polyline.totalDistanceM.takeIf { it.isFinite() && it > 0f }
        val selectedDistancePoint = selectedDistanceM
            ?.let { distance -> pctFromDistance(distance, totalDistanceM) }
            ?.let { distPct -> findLatLngForDistPct(orderedPoints, distPct) }
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
        LaunchedEffect(selectedDistancePoint, mapLoaded) {
            val marker = selectedDistancePoint ?: return@LaunchedEffect
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

                selectedDistancePoint?.let { marker ->
                    com.google.maps.android.compose.Circle(
                        center = marker,
                        radius = 6.2,
                        fillColor = Color.White.copy(alpha = 0.95f),
                        strokeColor = Color(0xFF0D47A1),
                        strokeWidth = 2.5f
                    )
                    com.google.maps.android.compose.Circle(
                        center = marker,
                        radius = 3.1,
                        fillColor = Color(0xFF42A5F5),
                        strokeColor = Color.White,
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}

@Composable
private fun RunDynamicsAnalysisSection(
    dynamicsInsights: RunDynamicsInsights?,
    mapData: RunMapData?,
    runDistanceMeters: Float?,
    selectedDistanceM: Float?,
    events: List<RunEvent>,
    speedPoints: List<ChartPoint>,
    accelerationPoints: List<ChartPoint>,
    onDistanceSelected: (Float) -> Unit
) {
    Text(
        text = tr(
            "What happened on the trail",
            "Que paso en el sendero"
        ),
        style = MaterialTheme.typography.titleSmall
    )
    if (dynamicsInsights == null) {
        Text(
            text = tr(
                "There is not enough data yet to explain key points.",
                "Aun no hay datos suficientes para explicar puntos clave."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val totalDistanceM = runDistanceMeters?.takeIf { it.isFinite() && it > 0f }
        ?: mapData?.polyline?.totalDistanceM?.takeIf { it.isFinite() && it > 0f }
    val groupedInsights = if (totalDistanceM == null) {
        emptyList()
    } else {
        buildTrailPointInsights(
            dynamicsInsights = dynamicsInsights,
            events = events,
            totalDistanceM = totalDistanceM
        )
    }
    val focusDistanceM = selectedDistanceM
        ?: groupedInsights.firstOrNull()?.distanceM
        ?: dynamicsInsights.speedDrops.firstOrNull()?.midDistanceM
        ?: dynamicsInsights.decelerationDrops.firstOrNull()?.distanceM

    val focusSpeedKmh = if (focusDistanceM != null && totalDistanceM != null) {
        interpolatedChartValueAtDist(speedPoints, focusDistanceM, totalDistanceM)
    } else null
    val focusAccelerationMs2 = if (focusDistanceM != null && totalDistanceM != null) {
        interpolatedChartValueAtDist(accelerationPoints, focusDistanceM, totalDistanceM)
    } else null
    val focusEvents = if (focusDistanceM != null && totalDistanceM != null) {
        nearbyEvents(events, focusDistanceM, totalDistanceM, maxDeltaM = 22f)
            .filter { it.type != EventType.HARSHNESS_BURST }
    } else emptyList()
    val focusTip = buildFocusAdvice(
        focusAccelerationMs2 = focusAccelerationMs2,
        focusEvents = focusEvents
    )

    val focusTitle = when {
        focusAccelerationMs2 != null && focusAccelerationMs2 <= -1.2f -> tr(
            "Strong braking at this point.",
            "Frenada fuerte en este punto."
        )
        focusAccelerationMs2 != null && focusAccelerationMs2 <= -0.5f -> tr(
            "Noticeable speed control here.",
            "Control marcado de velocidad en esta zona."
        )
        focusAccelerationMs2 != null && focusAccelerationMs2 >= 0.7f -> tr(
            "You are recovering speed here.",
            "Aqui vuelves a ganar velocidad."
        )
        else -> tr(
            "Transition section with moderate load.",
            "Seccion de transicion con carga moderada."
        )
    }
    val focusDetail = buildString {
        focusDistanceM?.let { append(tr("Position", "Ubicacion")); append(": "); append(formatDistanceLabel(it)); append(". ") }
        focusSpeedKmh?.let { append(tr("Speed", "Velocidad")); append(": "); append(String.format(Locale.US, "%.1f km/h", it)); append(". ") }
        focusAccelerationMs2?.let { acceleration ->
            val speedShiftKmh = kotlin.math.abs(acceleration) * 3.6f
            when {
                acceleration < -0.05f -> {
                    append(
                        tr(
                            "You lost about ${String.format(Locale.US, "%.1f", speedShiftKmh)} km/h here.",
                            "Perdiste aprox ${String.format(Locale.US, "%.1f", speedShiftKmh)} km/h en este punto."
                        )
                    )
                    append(" ")
                }
                acceleration > 0.05f -> {
                    append(
                        tr(
                            "You gained about ${String.format(Locale.US, "%.1f", speedShiftKmh)} km/h here.",
                            "Ganaste aprox ${String.format(Locale.US, "%.1f", speedShiftKmh)} km/h en este punto."
                        )
                    )
                    append(" ")
                }
                else -> Unit
            }
        }
        if (focusEvents.isNotEmpty()) {
            append(
                tr(
                    "Nearby events: ",
                    "Eventos cercanos: "
                )
            )
            append(focusEvents.take(2).joinToString(", ") { eventTypeShortCode(it.type) })
            append(".")
        }
    }.trim()

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr("Selected point", "Punto seleccionado"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = focusTitle,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = focusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            focusTip?.let { tip ->
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            focusDistanceM?.let {
                AnalysisFocusMap(
                    mapData = mapData,
                    runDistanceMeters = totalDistanceM,
                    focusDistanceM = it
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = tr("Tap a point to inspect it on the map:", "Toca un punto para verlo en el mapa:"),
        style = MaterialTheme.typography.bodyMedium
    )

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (groupedInsights.isEmpty()) {
                Text(
                    text = tr(
                        "No clear drop zones were detected in this run.",
                        "No se detectaron zonas claras de caida en esta bajada."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                groupedInsights.forEach { insight ->
                    ObservationItem(
                        distanceM = insight.distanceM,
                        focusDistanceM = focusDistanceM,
                        onClick = onDistanceSelected,
                        title = insight.title,
                        detail = insight.detail
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservationItem(
    distanceM: Float,
    focusDistanceM: Float?,
    title: String,
    detail: String,
    onClick: (Float) -> Unit
) {
    val isSelected = focusDistanceM != null && kotlin.math.abs(distanceM - focusDistanceM) <= 8f
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(distanceM) },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class TrailObservationKind {
    SPEED_DROP,
    BRAKING,
    RECOVERY,
    LANDING,
    IMPACT,
    TRANSITION
}

private data class TrailObservationCandidate(
    val distanceM: Float,
    val kind: TrailObservationKind,
    val speedDropKmh: Float? = null,
    val decelerationAbsMs2: Float? = null
)

private data class TrailPointInsight(
    val distanceM: Float,
    val title: String,
    val detail: String
)

@Composable
private fun buildFocusAdvice(
    focusAccelerationMs2: Float?,
    focusEvents: List<RunEvent>
): String? {
    val hasLanding = focusEvents.any { it.type == EventType.LANDING }
    val hasImpact = focusEvents.any { it.type == EventType.IMPACT_PEAK }
    return when {
        hasLanding -> tr(
            "Tip: Absorb this landing with bent elbows and knees, then look to the exit.",
            "Consejo: Absorbe este aterrizaje con codos y rodillas flexionados y mira la salida."
        )
        hasImpact -> tr(
            "Tip: Lighten the bike just before this hit or choose a cleaner line.",
            "Consejo: Descarga peso de la bici justo antes del golpe o busca una linea mas limpia."
        )
        focusAccelerationMs2 != null && focusAccelerationMs2 <= -1.0f -> tr(
            "Tip: Start braking a little earlier and release before the exit to keep flow.",
            "Consejo: Comienza a frenar un poco antes y suelta antes de la salida para mantener fluidez."
        )
        focusAccelerationMs2 != null && focusAccelerationMs2 <= -0.45f -> tr(
            "Tip: Brake progressively and keep the bike stable to preserve traction.",
            "Consejo: Frena progresivo y manten la bici estable para conservar traccion."
        )
        focusAccelerationMs2 != null && focusAccelerationMs2 >= 0.75f -> tr(
            "Tip: Keep your eyes on the next section and push speed only with control.",
            "Consejo: Mira la siguiente seccion y acelera solo con control."
        )
        else -> null
    }
}

@Composable
private fun buildTrailPointInsights(
    dynamicsInsights: RunDynamicsInsights,
    events: List<RunEvent>,
    totalDistanceM: Float
): List<TrailPointInsight> {
    val candidates = mutableListOf<TrailObservationCandidate>()

    dynamicsInsights.speedDrops.forEach { drop ->
        candidates += TrailObservationCandidate(
            distanceM = drop.midDistanceM,
            kind = TrailObservationKind.SPEED_DROP,
            speedDropKmh = drop.dropKmh
        )
    }
    dynamicsInsights.decelerationDrops.forEach { drop ->
        candidates += TrailObservationCandidate(
            distanceM = drop.distanceM,
            kind = TrailObservationKind.BRAKING,
            decelerationAbsMs2 = kotlin.math.abs(drop.accelerationMs2)
        )
    }
    dynamicsInsights.keyPoints.forEach { point ->
        val kind = when (point.trend) {
            KeyTrackTrend.HARD_BRAKING -> TrailObservationKind.BRAKING
            KeyTrackTrend.ACCELERATING -> TrailObservationKind.RECOVERY
            KeyTrackTrend.STABLE -> TrailObservationKind.TRANSITION
        }
        candidates += TrailObservationCandidate(
            distanceM = point.distanceM,
            kind = kind
        )
    }
    events.forEach { event ->
        if (event.type == EventType.HARSHNESS_BURST) return@forEach
        val distanceM = distanceFromPct(event.distPct, totalDistanceM) ?: return@forEach
        val kind = when (event.type) {
            EventType.LANDING -> TrailObservationKind.LANDING
            EventType.IMPACT_PEAK -> TrailObservationKind.IMPACT
            else -> null
        } ?: return@forEach
        candidates += TrailObservationCandidate(
            distanceM = distanceM,
            kind = kind
        )
    }

    if (candidates.isEmpty()) return emptyList()

    val mergeThresholdM = (totalDistanceM * 0.008f).coerceIn(8f, 14f)
    val clusters = mutableListOf<MutableList<TrailObservationCandidate>>()
    candidates.sortedBy { it.distanceM }.forEach { candidate ->
        val lastCluster = clusters.lastOrNull()
        if (lastCluster == null) {
            clusters += mutableListOf(candidate)
            return@forEach
        }
        val clusterCenter = lastCluster.map { it.distanceM }.average().toFloat()
        if (kotlin.math.abs(candidate.distanceM - clusterCenter) <= mergeThresholdM) {
            lastCluster += candidate
        } else {
            clusters += mutableListOf(candidate)
        }
    }

    return clusters.map { cluster ->
        val kinds = cluster.map { it.kind }.toSet()
        val distanceM = cluster.map { it.distanceM }.average().toFloat()
        val title = when {
            TrailObservationKind.BRAKING in kinds -> tr("Braking zone", "Zona de frenada")
            TrailObservationKind.LANDING in kinds -> tr("Landing point", "Punto de aterrizaje")
            TrailObservationKind.IMPACT in kinds -> tr("Impact point", "Punto de impacto")
            TrailObservationKind.SPEED_DROP in kinds -> tr("Speed loss zone", "Zona de perdida de velocidad")
            TrailObservationKind.RECOVERY in kinds -> tr("Speed recovery zone", "Zona de recuperacion de velocidad")
            else -> tr("Transition zone", "Zona de transicion")
        }

        val details = mutableListOf<String>()
        val maxSpeedDrop = cluster.mapNotNull { it.speedDropKmh }.maxOrNull()
        if (maxSpeedDrop != null) {
            details += tr(
                "You lost about ${String.format(Locale.US, "%.1f", maxSpeedDrop)} km/h.",
                "Perdiste aprox ${String.format(Locale.US, "%.1f", maxSpeedDrop)} km/h."
            )
        }
        val maxDecel = cluster.mapNotNull { it.decelerationAbsMs2 }.maxOrNull()
        if (maxDecel != null) {
            val speedLossRateKmh = maxDecel * 3.6f
            details += tr(
                "Braking here removes speed at about ${String.format(Locale.US, "%.1f", speedLossRateKmh)} km/h per second.",
                "Al frenar aqui pierdes velocidad a aprox ${String.format(Locale.US, "%.1f", speedLossRateKmh)} km/h por segundo."
            )
        }
        val landingCount = cluster.count { it.kind == TrailObservationKind.LANDING }
        if (landingCount > 0) {
            details += if (landingCount == 1) {
                tr("Landing event detected.", "Se detecta un aterrizaje.")
            } else {
                tr("$landingCount landing events detected.", "Se detectan $landingCount aterrizajes.")
            }
        }
        val impactCount = cluster.count { it.kind == TrailObservationKind.IMPACT }
        if (impactCount > 0) {
            details += if (impactCount == 1) {
                tr("Impact event detected.", "Se detecta un impacto.")
            } else {
                tr("$impactCount impact events detected.", "Se detectan $impactCount impactos.")
            }
        }
        if (TrailObservationKind.RECOVERY in kinds && TrailObservationKind.BRAKING !in kinds) {
            details += tr(
                "You start recovering speed here.",
                "Aqui empiezas a recuperar velocidad."
            )
        }
        val tip = when {
            TrailObservationKind.LANDING in kinds -> tr(
                "Tip: Stay loose on landing and prepare the next movement early.",
                "Consejo: Mantente suelto en el aterrizaje y prepara el siguiente movimiento temprano."
            )
            TrailObservationKind.IMPACT in kinds -> tr(
                "Tip: Unweight the bike before this impact or open your line.",
                "Consejo: Descarga la bici antes de este impacto o abre la linea."
            )
            TrailObservationKind.BRAKING in kinds -> tr(
                "Tip: Brake before the turn-in and release toward the exit.",
                "Consejo: Frena antes de entrar y suelta hacia la salida."
            )
            TrailObservationKind.RECOVERY in kinds -> tr(
                "Tip: Keep your eyes ahead and build speed only when traction is steady.",
                "Consejo: Mira adelante y gana velocidad solo cuando la traccion este firme."
            )
            else -> null
        }
        tip?.let { details += it }
        if (details.isEmpty()) {
            details += tr(
                "Section with stable behavior.",
                "Seccion con comportamiento estable."
            )
        }

        TrailPointInsight(
            distanceM = distanceM,
            title = title,
            detail = details.distinct().joinToString(" ")
        )
    }.sortedBy { it.distanceM }
}

@Composable
private fun AnalysisFocusMap(
    mapData: RunMapData?,
    runDistanceMeters: Float?,
    focusDistanceM: Float
) {
    val points = mapData?.polyline?.points.orEmpty()
    if (mapData == null || points.size < 2) return
    if (!BuildConfig.HAS_MAPS_API_KEY) return

    val orderedPoints = remember(points) { points.sortedBy { it.distPct } }
    val marker = remember(orderedPoints, focusDistanceM, runDistanceMeters) {
        pctFromDistance(focusDistanceM, runDistanceMeters)
            ?.let { findLatLngForDistPct(orderedPoints, it) }
    } ?: return

    var mapLoaded by remember(points) { mutableStateOf(false) }
    val boundsBuilder = remember(points) {
        LatLngBounds.builder().apply {
            points.forEach { include(LatLng(it.lat, it.lon)) }
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(points.first().lat, points.first().lon),
            14f
        )
    }

    LaunchedEffect(boundsBuilder, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 90)
            )
        }
    }
    LaunchedEffect(marker, mapLoaded) {
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
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.HYBRID),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                mapToolbarEnabled = false,
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
            com.google.maps.android.compose.Circle(
                center = marker,
                radius = 6.2,
                fillColor = Color.White.copy(alpha = 0.95f),
                strokeColor = Color(0xFF0D47A1),
                strokeWidth = 2.5f
            )
            com.google.maps.android.compose.Circle(
                center = marker,
                radius = 3.1,
                fillColor = Color(0xFF42A5F5),
                strokeColor = Color.White,
                strokeWidth = 1.5f
            )
        }
    }
}

private data class SpeedDropInsight(
    val startDistanceM: Float,
    val endDistanceM: Float,
    val midDistanceM: Float,
    val dropKmh: Float,
    val nearbyEventCode: String?
)

private data class DecelerationDropInsight(
    val distanceM: Float,
    val accelerationMs2: Float,
    val nearbyEventCode: String?
)

private enum class KeyTrackTrend {
    HARD_BRAKING,
    ACCELERATING,
    STABLE
}

private data class KeyTrackPointInsight(
    val distanceM: Float,
    val speedKmh: Float?,
    val accelerationMs2: Float?,
    val eventCodes: List<String>,
    val trend: KeyTrackTrend
)

private data class RunDynamicsInsights(
    val speedDropThresholdKmh: Float,
    val decelerationThresholdMs2: Float,
    val speedDrops: List<SpeedDropInsight>,
    val decelerationDrops: List<DecelerationDropInsight>,
    val keyPoints: List<KeyTrackPointInsight>
)

private fun buildRunDynamicsInsights(
    events: List<RunEvent>,
    speedPoints: List<ChartPoint>,
    accelerationPoints: List<ChartPoint>,
    totalDistanceM: Float?
): RunDynamicsInsights? {
    val totalDistance = totalDistanceM?.takeIf { it.isFinite() && it > 0f } ?: return null
    val cleanSpeedPoints = speedPoints
        .filter { it.x.isFinite() && it.y.isFinite() }
        .sortedBy { it.x }
    val cleanAccelerationPoints = accelerationPoints
        .filter { it.x.isFinite() && it.y.isFinite() }
        .sortedBy { it.x }
    if (cleanSpeedPoints.size < 2 && cleanAccelerationPoints.isEmpty()) return null

    val rawSpeedDrops = cleanSpeedPoints.zipWithNext().mapNotNull { (prev, curr) ->
        val delta = curr.y - prev.y
        if (!delta.isFinite() || delta >= 0f) return@mapNotNull null
        val midDist = ((prev.x + curr.x) / 2f).coerceIn(0f, totalDistance)
        SpeedDropInsight(
            startDistanceM = prev.x.coerceIn(0f, totalDistance),
            endDistanceM = curr.x.coerceIn(0f, totalDistance),
            midDistanceM = midDist,
            dropKmh = -delta,
            nearbyEventCode = nearestEventCode(events, midDist, totalDistance)
        )
    }
    val speedDropThreshold = if (rawSpeedDrops.isEmpty()) {
        0f
    } else {
        kotlin.math.max(0.8f, percentile(rawSpeedDrops.map { it.dropKmh }.sorted(), 0.55f))
    }
    val significantSpeedDrops = rawSpeedDrops
        .filter { it.dropKmh >= speedDropThreshold }
        .sortedByDescending { it.dropKmh }
        .take(10)

    val rawDecelerationDrops = cleanAccelerationPoints
        .filter { it.y < 0f }
        .map { point ->
            DecelerationDropInsight(
                distanceM = point.x.coerceIn(0f, totalDistance),
                accelerationMs2 = point.y,
                nearbyEventCode = nearestEventCode(events, point.x, totalDistance)
            )
        }
    val decelerationThreshold = if (rawDecelerationDrops.isEmpty()) {
        0f
    } else {
        val magnitudes = rawDecelerationDrops.map { -it.accelerationMs2 }.sorted()
        kotlin.math.max(0.2f, percentile(magnitudes, 0.60f))
    }
    val significantDecelerationDrops = rawDecelerationDrops
        .filter { -it.accelerationMs2 >= decelerationThreshold }
        .sortedBy { it.accelerationMs2 }
        .take(10)

    val candidateKeyDistances = mutableListOf<Float>().apply {
        addAll(significantSpeedDrops.map { it.midDistanceM })
        addAll(significantDecelerationDrops.map { it.distanceM })
        addAll(
            events.sortedByDescending { it.severity }
                .take(4)
                .mapNotNull { event -> distanceFromPct(event.distPct, totalDistance) }
        )
        if (isEmpty()) addAll(listOf(totalDistance * 0.2f, totalDistance * 0.5f, totalDistance * 0.8f))
    }

    val keyDistances = candidateKeyDistances.sorted().fold(mutableListOf<Float>()) { acc, dist ->
        if (acc.none { kotlin.math.abs(it - dist) < 12f }) {
            acc.add(dist.coerceIn(0f, totalDistance))
        }
        acc
    }.take(8)

    val keyPoints = keyDistances.map { dist ->
        val speed = nearestChartValueAtDist(cleanSpeedPoints, dist, totalDistance)
        val acceleration = nearestChartValueAtDist(cleanAccelerationPoints, dist, totalDistance)
        val nearbyEventCodes = nearbyEvents(events, dist, totalDistance, 25f)
            .take(2)
            .map { eventTypeShortCode(it.type) }
        val trend = when {
            acceleration != null && acceleration <= -0.55f -> KeyTrackTrend.HARD_BRAKING
            acceleration != null && acceleration >= 0.55f -> KeyTrackTrend.ACCELERATING
            else -> KeyTrackTrend.STABLE
        }
        KeyTrackPointInsight(
            distanceM = dist,
            speedKmh = speed,
            accelerationMs2 = acceleration,
            eventCodes = nearbyEventCodes,
            trend = trend
        )
    }

    return RunDynamicsInsights(
        speedDropThresholdKmh = speedDropThreshold,
        decelerationThresholdMs2 = decelerationThreshold,
        speedDrops = significantSpeedDrops,
        decelerationDrops = significantDecelerationDrops,
        keyPoints = keyPoints
    )
}

private fun findLatLngForDistPct(
    points: List<com.dhmeter.domain.model.GpsPoint>,
    distPct: Float
): LatLng? {
    if (points.isEmpty()) return null
    val sorted = points.sortedBy { it.distPct }
    val target = distPct.coerceIn(0f, 100f)
    if (target <= sorted.first().distPct) return LatLng(sorted.first().lat, sorted.first().lon)
    if (target >= sorted.last().distPct) return LatLng(sorted.last().lat, sorted.last().lon)

    for (index in 0 until sorted.lastIndex) {
        val a = sorted[index]
        val b = sorted[index + 1]
        if (target < a.distPct || target > b.distPct) continue
        val range = b.distPct - a.distPct
        if (kotlin.math.abs(range) < 1e-6f) {
            return LatLng(a.lat, a.lon)
        }
        val t = ((target - a.distPct) / range).coerceIn(0f, 1f)
        val lat = a.lat + (b.lat - a.lat) * t
        val lon = a.lon + (b.lon - a.lon) * t
        return LatLng(lat, lon)
    }

    val nearest = sorted.minByOrNull { point ->
        kotlin.math.abs(point.distPct - target)
    } ?: return null
    return LatLng(nearest.lat, nearest.lon)
}

private fun nearestEventCode(
    events: List<RunEvent>,
    distanceM: Float,
    totalDistanceM: Float,
    maxDeltaM: Float = 25f
): String? {
    val targetPct = pctFromDistance(distanceM, totalDistanceM) ?: return null
    val maxDeltaPct = (maxDeltaM / totalDistanceM * 100f).coerceAtLeast(0.5f)
    return events
        .minByOrNull { event -> kotlin.math.abs(event.distPct - targetPct) }
        ?.takeIf { kotlin.math.abs(it.distPct - targetPct) <= maxDeltaPct }
        ?.let { eventTypeShortCode(it.type) }
}

private fun nearbyEvents(
    events: List<RunEvent>,
    distanceM: Float,
    totalDistanceM: Float,
    maxDeltaM: Float
): List<RunEvent> {
    val targetPct = pctFromDistance(distanceM, totalDistanceM) ?: return emptyList()
    val maxDeltaPct = (maxDeltaM / totalDistanceM * 100f).coerceAtLeast(0.5f)
    return events
        .filter { kotlin.math.abs(it.distPct - targetPct) <= maxDeltaPct }
        .sortedBy { kotlin.math.abs(it.distPct - targetPct) }
}

private fun nearestChartValueAtDist(
    points: List<ChartPoint>,
    distanceM: Float,
    totalDistanceM: Float
): Float? {
    if (points.isEmpty()) return null
    return interpolatedChartValueAtDist(points, distanceM, totalDistanceM)
}

private fun interpolatedChartValueAtDist(
    points: List<ChartPoint>,
    distanceM: Float,
    totalDistanceM: Float
): Float? {
    if (points.isEmpty()) return null
    val target = distanceM.coerceIn(0f, totalDistanceM)
    val sorted = points.sortedBy { it.x }
    if (sorted.size == 1) return sorted.first().y
    if (target <= sorted.first().x) return sorted.first().y
    if (target >= sorted.last().x) return sorted.last().y

    for (index in 0 until sorted.lastIndex) {
        val a = sorted[index]
        val b = sorted[index + 1]
        if (target < a.x || target > b.x) continue
        val range = b.x - a.x
        if (kotlin.math.abs(range) < 1e-6f) return a.y
        val t = ((target - a.x) / range).coerceIn(0f, 1f)
        return a.y + (b.y - a.y) * t
    }

    return sorted.minByOrNull { kotlin.math.abs(it.x - target) }?.y
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
        EventType.HARSHNESS_BURST -> "VIB"
        else -> type.take(3).uppercase(Locale.US)
    }
}

@Composable
private fun eventTypeLabel(type: String): String {
    return when (type) {
        EventType.IMPACT_PEAK -> tr("Strong Impact", "Impacto fuerte")
        EventType.LANDING -> tr("Hard Landing", "Aterrizaje duro")
        EventType.HARSHNESS_BURST -> tr("Vibration event", "Evento de vibracion")
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
    val axisMax = kotlin.math.ceil(maxX / 10f) * 10f
    return AxisConfig(
        min = 0f,
        max = axisMax.coerceAtLeast(10f),
        label = "m",
        format = { value -> formatDistanceTick(value) }
    )
}

private fun formatDistanceTick(distanceM: Float): String {
    return if (distanceM >= 1000f) {
        String.format(Locale.US, "%.1fkm", distanceM / 1000f)
    } else {
        String.format(Locale.US, "%.0fm", distanceM)
    }
}

private fun formatDistanceLabel(distanceM: Float): String {
    return if (distanceM >= 1000f) {
        String.format(Locale.US, "%.2f km", distanceM / 1000f)
    } else {
        String.format(Locale.US, "%.1f m", distanceM)
    }
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
