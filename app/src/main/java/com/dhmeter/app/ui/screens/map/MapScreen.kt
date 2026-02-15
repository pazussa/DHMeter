package com.dropindh.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.*
import com.dhmeter.domain.model.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    runId: String,
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(runId) {
        viewModel.loadMapData(runId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = { Text(tr("Run Map", "Mapa de bajada")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "Atrás"))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.mapData != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapContent(
                            mapData = uiState.mapData!!,
                            selectedMetric = uiState.selectedMetric,
                            onMetricChange = viewModel::changeMetric,
                            onRoutePressed = viewModel::showSectionComparison,
                            onEventSelected = viewModel::selectEvent
                        )

                        if (uiState.isLoading) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tr("Updating map...", "Actualizando mapa..."),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        uiState.error?.let { errorMessage ->
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.mapData == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error ?: tr("No map data", "Sin datos de mapa"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = tr("No map data available", "No hay datos de mapa disponibles"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    // Only one bottom sheet at a time to avoid overlapping modal state issues.
    when {
        uiState.selectedEvent != null -> {
            EventBottomSheet(
                event = uiState.selectedEvent!!,
                onDismiss = viewModel::dismissEventSheet
            )
        }

        uiState.showSectionComparison && uiState.sectionComparison != null -> {
            SectionComparisonBottomSheet(
                comparison = uiState.sectionComparison!!,
                onDismiss = viewModel::dismissSectionComparison
            )
        }
    }
}

@Composable
private fun MapContent(
    mapData: RunMapData,
    selectedMetric: MapMetricType,
    onMetricChange: (MapMetricType) -> Unit,
    onRoutePressed: () -> Unit,
    onEventSelected: (MapEventMarker) -> Unit
) {
    val points = mapData.polyline.points
    val startPoint = points.firstOrNull()
    val endPoint = points.lastOrNull()
    var mapLoaded by remember { mutableStateOf(false) }
    
    // Calculate bounds
    val boundsBuilder = remember(points) {
        if (points.isEmpty()) null
        else LatLngBounds.builder().apply {
            points.forEach { include(LatLng(it.lat, it.lon)) }
        }
    }
    
    val cameraPositionState = rememberCameraPositionState {
        if (points.isNotEmpty()) {
            position = CameraPosition.fromLatLngZoom(
                LatLng(points.first().lat, points.first().lon),
                15f
            )
        }
    }

    // Fit bounds when map + route are ready.
    LaunchedEffect(boundsBuilder, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect
        boundsBuilder?.let { builder ->
            runCatching {
                val bounds = builder.build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 100)
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            if (points.size >= 2) {
                Polyline(
                    points = points.map { LatLng(it.lat, it.lon) },
                    color = Color(0xFF607D8B),
                    width = 4f,
                    clickable = true,
                    onClick = { onRoutePressed() }
                )
            }

            // Draw colored segments
            mapData.segments.forEach { segment ->
                Polyline(
                    points = listOf(
                        LatLng(segment.start.lat, segment.start.lon),
                        LatLng(segment.end.lat, segment.end.lon)
                    ),
                    color = segment.severity.toColor(),
                    width = 10f,
                    clickable = true,
                    onClick = { onRoutePressed() }
                )
            }

            startPoint?.let { start ->
                Marker(
                    state = MarkerState(position = LatLng(start.lat, start.lon)),
                    title = tr("Start", "Inicio"),
                    snippet = tr("0% of run", "0% de la bajada"),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            endPoint?.let { end ->
                Marker(
                    state = MarkerState(position = LatLng(end.lat, end.lon)),
                    title = tr("Finish", "Meta"),
                    snippet = tr("100% of run", "100% de la bajada"),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // Draw all event markers detected during the run.
            mapData.events.forEach { event ->
                Marker(
                    state = MarkerState(
                        position = LatLng(event.location.lat, event.location.lon)
                    ),
                    title = localizedMapEventTypeName(event.type),
                    snippet = tr("Tap for details", "Toca para ver detalles"),
                    icon = BitmapDescriptorFactory.defaultMarker(event.toMarkerHue()),
                    onClick = {
                        onEventSelected(event)
                        true
                    }
                )
            }
        }

        // GPS Quality Banner (if poor)
        if (mapData.polyline.gpsQuality == MapGpsQuality.POOR) {
            GpsQualityBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }

        // Controls overlay
        MapControls(
            selectedMetric = selectedMetric,
            onMetricChange = onMetricChange,
            onCenterRoute = {
                if (!mapLoaded) return@MapControls
                boundsBuilder?.let { builder ->
                    runCatching {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngBounds(builder.build(), 100)
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Legend
        MapLegend(
            metric = selectedMetric,
            percentiles = mapData.percentiles,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun GpsQualityBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = YellowWarning.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tr(
                    "GPS imprecise. Map is approximate.\nUse charts for analysis.",
                    "GPS impreciso. El mapa es aproximado.\nUsa las gráficas para analizar."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun MapControls(
    selectedMetric: MapMetricType,
    onMetricChange: (MapMetricType) -> Unit,
    onCenterRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Metric selector
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MapMetricType.entries.forEach { metric ->
                        FilterChip(
                            selected = selectedMetric == metric,
                            onClick = { onMetricChange(metric) },
                            label = { Text(localizedMetricName(metric)) }
                        )
                    }
                }
            }
        }

        // Route controls
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCenterRoute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("Center Route", "Centrar ruta"))
                }
            }
        }
    }
}

@Composable
private fun MapLegend(
    metric: MapMetricType,
    percentiles: MetricPercentiles,
    modifier: Modifier = Modifier
) {
    val hasThresholds = percentiles.p20 != 0f ||
            percentiles.p40 != 0f ||
            percentiles.p60 != 0f ||
            percentiles.p80 != 0f

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = localizedMetricName(metric),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasThresholds) {
                LegendItem(
                    color = SegmentSeverity.VERY_LOW.toColor(),
                    label = tr(
                        "<= P20 (${formatMetricThreshold(percentiles.p20)})",
                        "<= P20 (${formatMetricThreshold(percentiles.p20)})"
                    )
                )
                LegendItem(
                    color = SegmentSeverity.LOW.toColor(),
                    label = tr(
                        "P20-P40 (${formatMetricThreshold(percentiles.p40)})",
                        "P20-P40 (${formatMetricThreshold(percentiles.p40)})"
                    )
                )
                LegendItem(
                    color = SegmentSeverity.MEDIUM.toColor(),
                    label = tr(
                        "P40-P60 (${formatMetricThreshold(percentiles.p60)})",
                        "P40-P60 (${formatMetricThreshold(percentiles.p60)})"
                    )
                )
                LegendItem(
                    color = SegmentSeverity.HIGH.toColor(),
                    label = tr(
                        "P60-P80 (${formatMetricThreshold(percentiles.p80)})",
                        "P60-P80 (${formatMetricThreshold(percentiles.p80)})"
                    )
                )
                LegendItem(
                    color = SegmentSeverity.VERY_HIGH.toColor(),
                    label = tr("> P80", "> P80")
                )
            } else {
                LegendItem(
                    color = SegmentSeverity.MEDIUM.toColor(),
                    label = tr(
                        "No metric samples (neutral coloring)",
                        "Sin muestras de métrica (color neutro)"
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionComparisonBottomSheet(
    comparison: RunMapSectionComparison,
    onDismiss: () -> Unit
) {
    val currentIsFastest = comparison.currentRunId == comparison.fastestRunId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("Section Time Comparison", "Comparación de tiempos por sección"),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (currentIsFastest) {
                    tr(
                        "This run is the fastest reference on this track.",
                        "Esta bajada es la referencia más rápida en este track."
                    )
                } else {
                    tr(
                        "Compared against the fastest run on this track.",
                        "Comparada contra la bajada más rápida de este track."
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = tr(
                    "Current: ${formatMsCompact(comparison.currentDurationMs)}   Fastest: ${formatMsCompact(comparison.fastestDurationMs)}",
                    "Actual: ${formatMsCompact(comparison.currentDurationMs)}   Mas rápida: ${formatMsCompact(comparison.fastestDurationMs)}"
                ),
                style = MaterialTheme.typography.labelMedium
            )
            if (!comparison.hasMeasuredSplitTiming) {
                Text(
                    text = tr(
                        "Split timing is estimated from total duration for at least one run.",
                        "El tiempo por secciones se estima desde la duración total en al menos una bajada."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            HorizontalDivider()

            comparison.sections.forEach { section ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "S${section.sectionIndex} (${section.startDistPct.toInt()}-${section.endDistPct.toInt()}%)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = tr(
                                "Time: ${formatMs(section.currentSectionMs)}",
                                "Tiempo: ${formatMs(section.currentSectionMs)}"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = tr(
                                "Avg speed: ${formatSpeed(section.currentAvgSpeedMps)}",
                                "Velocidad prom.: ${formatSpeed(section.currentAvgSpeedMps)}"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    val delta = section.deltaVsFastestMs
                    val deltaText = formatVsFastestDelta(delta)
                    val deltaColor = when {
                        delta == null -> MaterialTheme.colorScheme.outline
                        delta < 0L -> GreenPositive
                        delta > 0L -> RedNegative
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelMedium,
                        color = deltaColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp, 4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventBottomSheet(
    event: MapEventMarker,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (event.type) {
                        MapEventType.LANDING -> Icons.Default.FlightLand
                        MapEventType.IMPACT_PEAK -> Icons.Default.Bolt
                        MapEventType.HARSHNESS_BURST -> Icons.Default.Vibration
                        MapEventType.OTHER -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (event.type) {
                        MapEventType.LANDING -> ChartStability
                        MapEventType.IMPACT_PEAK -> ChartImpact
                        MapEventType.HARSHNESS_BURST -> YellowWarning
                        MapEventType.OTHER -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = localizedMapEventTypeName(event.type),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            "At ${String.format(Locale.US, "%.1f", event.distPct)}% of run",
                            "En ${String.format(Locale.US, "%.1f", event.distPct)}% de la bajada"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Metrics
            Text(
                text = tr("Event Metrics", "Metricas del evento"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))

            (event.meta["peakG"] as? Number)?.let { peak ->
                MetricRow(
                    label = tr("Peak Force", "Fuerza pico"),
                    value = "${String.format(Locale.US, "%.1f", peak.toFloat())} G"
                )
            }
            
            (event.meta["energy300ms"] as? Number)?.let { energy ->
                MetricRow(
                    label = tr("Impact Energy", "Energia de impacto"),
                    value = String.format(Locale.US, "%.2f", energy.toFloat())
                )
            }
            
            (event.meta["recoveryMs"] as? Number)?.let { recovery ->
                MetricRow(
                    label = tr("Recovery Time", "Tiempo de recuperacion"),
                    value = "${recovery.toInt()} ms"
                )
            }

            (event.meta["rmsValue"] as? Number)?.let { rms ->
                MetricRow(
                    label = tr("Burst RMS", "RMS de rafaga"),
                    value = String.format(Locale.US, "%.3f", rms.toFloat())
                )
            }

            (event.meta["durationMs"] as? Number)?.let { duration ->
                MetricRow(
                    label = tr("Burst Duration", "Duracion de rafaga"),
                    value = "${duration.toInt()} ms"
                )
            }

            MetricRow(
                label = tr("Severity", "Severidad"),
                value = String.format(Locale.US, "%.1f", event.severity)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun SegmentSeverity.toColor(): Color = when (this) {
    SegmentSeverity.VERY_LOW -> Color(0xFF1B5E20)
    SegmentSeverity.LOW -> Color(0xFF4CAF50)
    SegmentSeverity.MEDIUM -> Color(0xFFFFC107)
    SegmentSeverity.HIGH -> Color(0xFFFF9800)
    SegmentSeverity.VERY_HIGH -> Color(0xFFD32F2F)
}

private fun MapEventMarker.toMarkerHue(): Float = when (type) {
    MapEventType.IMPACT_PEAK -> BitmapDescriptorFactory.HUE_RED
    MapEventType.LANDING -> BitmapDescriptorFactory.HUE_ORANGE
    MapEventType.HARSHNESS_BURST -> BitmapDescriptorFactory.HUE_YELLOW
    MapEventType.OTHER -> BitmapDescriptorFactory.HUE_VIOLET
}

private fun formatMetricThreshold(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun isSpanishLanguage(): Boolean {
    val appLanguage = AppCompatDelegate.getApplicationLocales().get(0)?.language
    val language = appLanguage ?: Locale.getDefault().language
    return language.lowercase(Locale.US).startsWith("es")
}

private fun localizedMetricName(metric: MapMetricType): String {
    if (!isSpanishLanguage()) {
        return when (metric) {
            MapMetricType.IMPACT -> "Impact"
            MapMetricType.HARSHNESS -> "Harshness"
            MapMetricType.STABILITY -> "Instability"
            MapMetricType.SPEED -> "Speed"
        }
    }
    return when (metric) {
        MapMetricType.IMPACT -> "Impacto"
        MapMetricType.HARSHNESS -> "Vibración"
        MapMetricType.STABILITY -> "Inestabilidad"
        MapMetricType.SPEED -> "Velocidad"
    }
}

private fun localizedMapEventTypeName(type: MapEventType): String {
    if (!isSpanishLanguage()) {
        return when (type) {
            MapEventType.IMPACT_PEAK -> "Strong Impact"
            MapEventType.LANDING -> "Hard Landing"
            MapEventType.HARSHNESS_BURST -> "Harshness Burst"
            MapEventType.OTHER -> "Event"
        }
    }
    return when (type) {
        MapEventType.IMPACT_PEAK -> "Impacto fuerte"
        MapEventType.LANDING -> "Aterrizaje duro"
        MapEventType.HARSHNESS_BURST -> "Rafaga de vibracion"
        MapEventType.OTHER -> "Evento"
    }
}

private fun formatMs(value: Long?): String {
    value ?: return "--"
    return if (value >= 1000L) {
        String.format(Locale.US, "%.2f s", value / 1000f)
    } else {
        "$value ms"
    }
}

private fun formatSpeed(speedMps: Float?): String {
    speedMps ?: return "--"
    return String.format(Locale.US, "%.1f km/h", speedMps * 3.6f)
}

private fun formatMsCompact(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (valueMs % 1000) / 10
    return String.format(Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
}

private fun formatVsFastestDelta(deltaMs: Long?): String {
    deltaMs ?: return "--"
    if (deltaMs == 0L) return if (isSpanishLanguage()) "Igual" else "Same"
    val absMs = kotlin.math.abs(deltaMs)
    val valueText = if (absMs >= 1000L) {
        String.format(Locale.US, "%.2f s", absMs / 1000f)
    } else {
        "$absMs ms"
    }
    return if (deltaMs < 0L) {
        if (isSpanishLanguage()) "-$valueText más rápido" else "-$valueText faster"
    } else {
        if (isSpanishLanguage()) "+$valueText más lento" else "+$valueText slower"
    }
}


