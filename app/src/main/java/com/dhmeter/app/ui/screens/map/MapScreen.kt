package com.dhmeter.app.ui.screens.map

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.ui.theme.*
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
        topBar = {
            TopAppBar(
                title = { Text("Run Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            showEvents = uiState.showEvents,
                            onMetricChange = viewModel::changeMetric,
                            onToggleEvents = viewModel::toggleEvents,
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
                                        text = "Updating map...",
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
                            text = uiState.error ?: "No map data",
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
                            text = "No map data available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    // Event bottom sheet
    uiState.selectedEvent?.let { event ->
        EventBottomSheet(
            event = event,
            onDismiss = viewModel::dismissEventSheet
        )
    }

    if (uiState.showSectionComparison && uiState.sectionComparison != null) {
        SectionComparisonBottomSheet(
            comparison = uiState.sectionComparison!!,
            onDismiss = viewModel::dismissSectionComparison
        )
    }
}

@Composable
private fun MapContent(
    mapData: RunMapData,
    selectedMetric: MapMetricType,
    showEvents: Boolean,
    onMetricChange: (MapMetricType) -> Unit,
    onToggleEvents: () -> Unit,
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
                    title = "Start",
                    snippet = "0% of run",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            endPoint?.let { end ->
                Marker(
                    state = MarkerState(position = LatLng(end.lat, end.lon)),
                    title = "Finish",
                    snippet = "100% of run",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // Draw event markers
            if (showEvents) {
                mapData.events.forEach { event ->
                    Marker(
                        state = MarkerState(
                            position = LatLng(event.location.lat, event.location.lon)
                        ),
                        title = event.type.displayName,
                        snippet = "Tap for details",
                        icon = BitmapDescriptorFactory.defaultMarker(event.toMarkerHue()),
                        onClick = {
                            onEventSelected(event)
                            true
                        }
                    )
                }
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
            showEvents = showEvents,
            onMetricChange = onMetricChange,
            onToggleEvents = onToggleEvents,
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
                text = "GPS imprecise. Map is approximate.\nUse charts for analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun MapControls(
    selectedMetric: MapMetricType,
    showEvents: Boolean,
    onMetricChange: (MapMetricType) -> Unit,
    onToggleEvents: () -> Unit,
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
                            label = { Text(metric.displayName) }
                        )
                    }
                }
            }
        }

        // Layers and visibility
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Events",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showEvents,
                        onCheckedChange = { onToggleEvents() }
                    )
                }
                OutlinedButton(
                    onClick = onCenterRoute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Center Route")
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
                text = metric.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasThresholds) {
                LegendItem(
                    color = SegmentSeverity.VERY_LOW.toColor(),
                    label = "<= P20 (${formatMetricThreshold(percentiles.p20)})"
                )
                LegendItem(
                    color = SegmentSeverity.LOW.toColor(),
                    label = "P20-P40 (${formatMetricThreshold(percentiles.p40)})"
                )
                LegendItem(
                    color = SegmentSeverity.MEDIUM.toColor(),
                    label = "P40-P60 (${formatMetricThreshold(percentiles.p60)})"
                )
                LegendItem(
                    color = SegmentSeverity.HIGH.toColor(),
                    label = "P60-P80 (${formatMetricThreshold(percentiles.p80)})"
                )
                LegendItem(
                    color = SegmentSeverity.VERY_HIGH.toColor(),
                    label = "> P80"
                )
            } else {
                LegendItem(
                    color = SegmentSeverity.MEDIUM.toColor(),
                    label = "No metric samples (neutral coloring)"
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
                text = "Section Time Comparison",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (currentIsFastest) {
                    "This run is the fastest reference on this track."
                } else {
                    "Compared against the fastest run on this track."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "Current: ${formatMsCompact(comparison.currentDurationMs)}   Fastest: ${formatMsCompact(comparison.fastestDurationMs)}",
                style = MaterialTheme.typography.labelMedium
            )
            if (!comparison.hasMeasuredSplitTiming) {
                Text(
                    text = "Split timing is estimated from total duration for at least one run.",
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
                            text = "Time: ${formatMs(section.currentSectionMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Avg speed: ${formatSpeed(section.currentAvgSpeedMps)}",
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
                    },
                    contentDescription = null,
                    tint = when (event.type) {
                        MapEventType.LANDING -> ChartStability
                        MapEventType.IMPACT_PEAK -> ChartImpact
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = event.type.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "At ${String.format(Locale.US, "%.1f", event.distPct)}% of run",
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
                text = "Event Metrics",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))

            (event.meta["peakG"] as? Number)?.let { peak ->
                MetricRow(
                    label = "Peak Force",
                    value = "${String.format(Locale.US, "%.1f", peak.toFloat())} G"
                )
            }
            
            (event.meta["energy300ms"] as? Number)?.let { energy ->
                MetricRow(
                    label = "Impact Energy",
                    value = String.format(Locale.US, "%.2f", energy.toFloat())
                )
            }
            
            (event.meta["recoveryMs"] as? Number)?.let { recovery ->
                MetricRow(
                    label = "Recovery Time",
                    value = "${recovery.toInt()} ms"
                )
            }

            MetricRow(
                label = "Severity",
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
}

private fun formatMetricThreshold(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
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
    if (deltaMs == 0L) return "Same"
    val absMs = kotlin.math.abs(deltaMs)
    val valueText = if (absMs >= 1000L) {
        String.format(Locale.US, "%.2f s", absMs / 1000f)
    } else {
        "$absMs ms"
    }
    return if (deltaMs < 0L) {
        "-$valueText faster"
    } else {
        "+$valueText slower"
    }
}

