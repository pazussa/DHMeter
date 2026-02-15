package com.dropindh.app.ui.screens.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.*
import com.dhmeter.charts.components.ComparisonLineChart
import com.dhmeter.charts.model.AxisConfig
import com.dhmeter.charts.model.ChartPoint
import com.dhmeter.charts.model.ChartSeries
import com.dhmeter.domain.model.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    trackId: String,
    runIds: List<String>,
    onViewCharts: () -> Unit,
    onBack: () -> Unit,
    viewModel: CompareViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(trackId, runIds) {
        viewModel.loadComparison(trackId, runIds)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = {
                    Text(
                        tr(
                            "Compare ${runIds.size} Runs",
                            "Comparar ${runIds.size} bajadas"
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Atrás"))
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            ) {
                Button(
                    onClick = onViewCharts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = uiState.comparison != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        tr(
                            "View Charts (${runIds.size} runs)",
                            "Ver gráficas (${runIds.size} bajadas)"
                        )
                    )
                }
            }
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
            uiState.comparison != null -> {
                MultiRunComparisonContent(
                    comparison = uiState.comparison!!,
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
                    Text(uiState.error ?: tr("Could not load comparison", "No se pudo cargar la comparación"))
                }
            }
        }
    }
}

@Composable
private fun MultiRunComparisonContent(
    comparison: MultiRunComparisonResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Runs header (horizontal scroll for many runs)
        RunsHeader(runs = comparison.runs)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Verdict card
        VerdictCard(verdict = comparison.verdict)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Metrics comparison table
        Text(
            text = tr("Metrics Comparison", "Comparación de métricas"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = tr(
                "Lower score means smoother/less punishing.",
                "Menor puntaje significa más suave/menos castigador."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        MultiMetricsTable(
            runs = comparison.runs,
            comparisons = comparison.metricComparisons
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = tr("Speed Comparison", "Comparación de velocidad"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        SpeedComparisonChart(comparison = comparison)

        Spacer(modifier = Modifier.height(24.dp))

        comparison.mapComparison?.let { mapComparison ->
            Text(
                text = tr("Route Comparison", "Comparación de ruta"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            MapComparisonSection(
                mapComparison = mapComparison,
                totalRunCount = comparison.runs.size
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section analysis
        if (comparison.sectionInsights.isNotEmpty()) {
            Text(
                text = tr("Insights", "Insights"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            SectionInsights(insights = comparison.sectionInsights)
        }
    }
}

@Composable
private fun RunsHeader(runs: List<RunWithColor>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(runs) { runWithColor ->
            RunCard(
                runWithColor = runWithColor,
                dateFormat = dateFormat
            )
        }
    }
}

@Composable
private fun RunCard(
    runWithColor: RunWithColor,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(runWithColor.color), shape = MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = localizedRunLabel(runWithColor.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(runWithColor.color)
                )
            }
            Text(
                text = dateFormat.format(Date(runWithColor.run.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatDuration(runWithColor.run.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun VerdictCard(verdict: MultiRunVerdict) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (verdict.type) {
                MultiRunVerdict.Type.CLEAR_WINNER -> GreenPositive.copy(alpha = 0.1f)
                MultiRunVerdict.Type.MIXED -> YellowWarning.copy(alpha = 0.1f)
                MultiRunVerdict.Type.SIMILAR -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (verdict.type) {
                        MultiRunVerdict.Type.CLEAR_WINNER -> Icons.Default.EmojiEvents
                        MultiRunVerdict.Type.MIXED -> Icons.Default.SwapVert
                        MultiRunVerdict.Type.SIMILAR -> Icons.Default.DragHandle
                    },
                    contentDescription = null,
                    tint = when (verdict.type) {
                        MultiRunVerdict.Type.CLEAR_WINNER -> GreenPositive
                        MultiRunVerdict.Type.MIXED -> YellowWarning
                        MultiRunVerdict.Type.SIMILAR -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = localizedVerdictTitle(verdict),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = localizedInsightText(verdict.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpeedComparisonChart(comparison: MultiRunComparisonResult) {
    val chartSeries = remember(comparison) { buildSpeedComparisonSeries(comparison) }

    if (chartSeries.isEmpty()) {
        Text(
            text = tr("No speed data available", "No hay datos de velocidad disponibles"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val maxSpeed = chartSeries.flatMap { it.points }.maxOfOrNull { it.y } ?: 0f
        val axisMax = (ceil(maxSpeed / 5f) * 5f).coerceAtLeast(10f)

        ComparisonLineChart(
            series = chartSeries,
            xAxisConfig = AxisConfig(0f, 100f, label = tr("Distance %", "Distancia %")),
            yAxisConfig = AxisConfig(0f, axisMax, label = "km/h"),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

private fun buildSpeedComparisonSeries(comparison: MultiRunComparisonResult): List<ChartSeries> {
    val mapComparison = comparison.mapComparison

    return comparison.runs.mapIndexedNotNull { runIndex, runWithColor ->
        val sectionPoints = mapComparison?.sections
            ?.mapNotNull { section ->
                val speedMps = section.sectionAvgSpeedMps.getOrNull(runIndex)
                speedMps?.takeIf { it.isFinite() && it > 0f }?.let {
                    ChartPoint(
                        x = (section.startDistPct + section.endDistPct) / 2f,
                        y = it * 3.6f
                    )
                }
            }
            .orEmpty()
            .sortedBy { it.x }

        val avgSpeedMps = runWithColor.run.avgSpeed
        val points = when {
            sectionPoints.isNotEmpty() -> buildList {
                add(ChartPoint(0f, sectionPoints.first().y))
                addAll(sectionPoints)
                add(ChartPoint(100f, sectionPoints.last().y))
            }

            avgSpeedMps != null && avgSpeedMps > 0f -> {
                val speedKmh = avgSpeedMps * 3.6f
                listOf(ChartPoint(0f, speedKmh), ChartPoint(100f, speedKmh))
            }

            else -> emptyList()
        }.filter { it.x.isFinite() && it.y.isFinite() }

        if (points.isEmpty()) {
            null
        } else {
            ChartSeries(
                label = localizedRunLabel(runWithColor.label),
                points = points,
                color = Color(runWithColor.color)
            )
        }
    }
}

@Composable
private fun MultiMetricsTable(
    runs: List<RunWithColor>,
    comparisons: List<MultiMetricComparison>
) {
    val horizontalScroll = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row with run labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScroll),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = tr("Metric", "Métrica"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(132.dp)
                )
                runs.forEach { runWithColor ->
                    Text(
                        text = localizedRunLabel(runWithColor.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(runWithColor.color),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Metric rows
            comparisons.forEach { comparison ->
                MultiMetricRow(
                    comparison = comparison,
                    horizontalScroll = horizontalScroll
                )
            }
        }
    }
}

@Composable
private fun MapComparisonSection(
    mapComparison: MapComparisonData,
    totalRunCount: Int
) {
    var selectedSectionIndex by remember(mapComparison) { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mapComparison.runs.size < totalRunCount) {
            Text(
                text = tr(
                    "Showing ${mapComparison.runs.size}/$totalRunCount runs with GPS route data.",
                    "Mostrando ${mapComparison.runs.size}/$totalRunCount bajadas con ruta GPS."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Text(
            text = tr(
                "Tap an S marker on the map to highlight that section in the table.",
                "Toca un marcador S en el mapa para resaltar esa sección en la tabla."
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (!mapComparison.hasMeasuredSplitTiming) {
            Text(
                text = tr(
                    "Some runs lack timing profile data; section times are estimated from total duration.",
                    "Algunas bajadas no tienen perfil de tiempos; los tiempos por sección se estiman desde la duración total."
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            RouteOverlayMap(
                mapComparison = mapComparison,
                selectedSectionIndex = selectedSectionIndex,
                onSectionSelected = { selectedSectionIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }

        SplitSectionTable(
            mapComparison = mapComparison,
            selectedSectionIndex = selectedSectionIndex
        )
    }
}

@Composable
private fun RouteOverlayMap(
    mapComparison: MapComparisonData,
    selectedSectionIndex: Int?,
    onSectionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val runs = mapComparison.runs
    if (runs.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(tr("No route data available", "No hay datos de ruta disponibles"))
        }
    } else {
        val allPoints = remember(runs) {
            runs.flatMap { run -> run.polyline.points.map { LatLng(it.lat, it.lon) } }
        }
        val baselinePoints = runs.first().polyline.points
        var mapLoaded by remember { mutableStateOf(false) }
        val boundsBuilder = remember(allPoints) {
            if (allPoints.isEmpty()) null else LatLngBounds.builder().apply {
                allPoints.forEach { include(it) }
            }
        }
        val cameraPositionState = rememberCameraPositionState {
            val firstPoint = allPoints.firstOrNull()
            if (firstPoint != null) {
                position = CameraPosition.fromLatLngZoom(firstPoint, 14f)
            }
        }

        LaunchedEffect(boundsBuilder, mapLoaded) {
            if (!mapLoaded) return@LaunchedEffect
            boundsBuilder?.let { builder ->
                runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(builder.build(), 90)
                    )
                }
            }
        }

        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.HYBRID),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                mapToolbarEnabled = true,
                compassEnabled = true
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            runs.forEachIndexed { index, run ->
                val polylinePoints = run.polyline.points.map { LatLng(it.lat, it.lon) }
                if (polylinePoints.size >= 2) {
                    Polyline(
                        points = polylinePoints,
                        color = Color(run.color),
                        width = if (index == mapComparison.baselineRunIndex) 10f else 7f,
                        zIndex = if (index == mapComparison.baselineRunIndex) 2f else 1f
                    )
                }
            }

            baselinePoints.firstOrNull()?.let { start ->
                Marker(
                    state = MarkerState(LatLng(start.lat, start.lon)),
                    title = tr("Start", "Inicio"),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            baselinePoints.lastOrNull()?.let { end ->
                Marker(
                    state = MarkerState(LatLng(end.lat, end.lon)),
                    title = tr("Finish", "Meta"),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // Sector markers (S1..Sn) based on baseline route.
            mapComparison.sections.dropLast(1).forEach { section ->
                findPointNearDistPct(baselinePoints, section.endDistPct)?.let { point ->
                    val isSelected = selectedSectionIndex == section.sectionIndex
                    Marker(
                        state = MarkerState(LatLng(point.lat, point.lon)),
                        title = "S${section.sectionIndex}",
                        snippet = "${section.startDistPct.toInt()}-${section.endDistPct.toInt()}%",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (isSelected) BitmapDescriptorFactory.HUE_YELLOW
                            else BitmapDescriptorFactory.HUE_ORANGE
                        ),
                        onClick = {
                            onSectionSelected(section.sectionIndex)
                            false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitSectionTable(
    mapComparison: MapComparisonData,
    selectedSectionIndex: Int?
) {
    val horizontalScroll = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Section", "Sección"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(132.dp)
                )
                mapComparison.runs.forEach { run ->
                    Text(
                        text = localizedRunLabel(run.runLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(run.color),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            mapComparison.sections.forEach { section ->
                val isSelected = selectedSectionIndex == section.sectionIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                Color.Transparent
                            },
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .horizontalScroll(horizontalScroll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            if (isSelected) append("> ")
                            append("S${section.sectionIndex} (${section.startDistPct.toInt()}-${section.endDistPct.toInt()}%)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(132.dp)
                    )

                    section.sectionTimesMs.forEachIndexed { runIndex, splitMs ->
                        val isBest = section.bestRunIndex == runIndex
                        val timeText = formatMs(splitMs)
                        val color = if (isBest) GreenPositive else MaterialTheme.colorScheme.onSurface

                        Column(
                            modifier = Modifier.width(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isBest) "* $timeText" else timeText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = formatSpeed(section.sectionAvgSpeedMps.getOrNull(runIndex)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiMetricRow(
    comparison: MultiMetricComparison,
    horizontalScroll: ScrollState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(horizontalScroll),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localizedMetricName(comparison.metricName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(132.dp)
        )
        
        comparison.values.forEachIndexed { index, value ->
            val isBest = comparison.bestRunIndex == index
            val displayValue = when {
                comparison.metricName == "Duration" && value != null -> formatDuration((value * 1000).toLong())
                comparison.metricName == "Max Speed" && value != null -> String.format(Locale.US, "%.1f km/h", value)
                else -> value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
            }
            
            Box(
                modifier = Modifier.width(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBest) "* $displayValue" else displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBest) GreenPositive else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionInsights(insights: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            insights.forEach { insight ->
                Row {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = localizedInsightText(insight),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}

private fun isSpanishLanguage(): Boolean {
    val appLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.language
    val language = appLanguage ?: Locale.getDefault().language
    return language.lowercase(Locale.US).startsWith("es")
}

private fun localizedRunLabel(label: String): String {
    if (!isSpanishLanguage()) return label
    return if (label.startsWith("Run ")) {
        "Bajada ${label.removePrefix("Run ").trim()}"
    } else {
        label
    }
}

private fun localizedMetricName(metricName: String): String {
    if (!isSpanishLanguage()) return metricName
    return when (metricName) {
        "Impact" -> "Impacto"
        "Harshness" -> "Vibración"
        "Stability" -> "Inestabilidad"
        "Landing Quality" -> "Calidad de aterrizaje"
        "Duration" -> "Duración"
        "Max Speed" -> "Velocidad máxima"
        else -> metricName
    }
}

private fun localizedVerdictTitle(verdict: MultiRunVerdict): String {
    if (!isSpanishLanguage()) return verdict.title
    return when (verdict.type) {
        MultiRunVerdict.Type.CLEAR_WINNER -> "${localizedRunLabel(verdict.bestRunLabel)} fue la más suave"
        MultiRunVerdict.Type.MIXED -> "Resultados mixtos"
        MultiRunVerdict.Type.SIMILAR -> "Rendimiento similar"
    }
}

private fun localizedInsightText(text: String): String {
    if (!isSpanishLanguage()) return text
    return text
        .replace("Most metrics are similar across runs.", "La mayoría de métricas son similares entre bajadas.")
        .replace("Metrics are too close to declare a clear advantage.", "Las métricas están demasiado cerca para una ventaja clara.")
        .replace("No significant differences detected between runs", "No se detectaron diferencias significativas entre bajadas")
        .replace("Mixed results", "Resultados mixtos")
        .replace("Similar performance", "Rendimiento similar")
        .replace("Best ", "Mejor ")
        .replace(" varies by ", " varia en ")
        .replace("% across runs", "% entre bajadas")
        .replace(" spread is ", " tiene un rango de ")
        .replace(" points", " puntos")
        .replace(" has best ", " tiene mejor ")
        .replace(" gained ", " ganó ")
        .replace(" lost ", " perdió ")
        .replace(" in S", " en S")
        .replace("Impact", "Impacto")
        .replace("Harshness", "Vibración")
        .replace("Stability", "Inestabilidad")
        .replace("Landing Quality", "Calidad de aterrizaje")
        .replace("Duration", "Duración")
        .replace("Max Speed", "Velocidad máxima")
}

private fun formatMs(value: Long?): String {
    value ?: return "--"
    return if (value >= 1000L) {
        String.format(Locale.US, "%.2f s", value / 1000f)
    } else {
        "${value} ms"
    }
}

private fun formatSpeed(speedMps: Float?): String {
    speedMps ?: return "--"
    return String.format(Locale.US, "%.1f km/h", speedMps * 3.6f)
}

private fun findPointNearDistPct(points: List<GpsPoint>, targetPct: Float): GpsPoint? {
    if (points.isEmpty()) return null
    return points.minByOrNull { point -> abs(point.distPct - targetPct) }
}



