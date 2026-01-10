package com.dhmeter.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                uiState.mapData != null -> {
                    MapContent(
                        mapData = uiState.mapData!!,
                        selectedMetric = uiState.selectedMetric,
                        showEvents = uiState.showEvents,
                        onMetricChange = viewModel::changeMetric,
                        onToggleEvents = viewModel::toggleEvents,
                        onEventSelected = viewModel::selectEvent
                    )
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
}

@Composable
private fun MapContent(
    mapData: RunMapData,
    selectedMetric: MapMetricType,
    showEvents: Boolean,
    onMetricChange: (MapMetricType) -> Unit,
    onToggleEvents: () -> Unit,
    onEventSelected: (MapEventMarker) -> Unit
) {
    val points = mapData.polyline.points
    
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

    // Fit bounds when loaded
    LaunchedEffect(boundsBuilder) {
        boundsBuilder?.let { builder ->
            val bounds = builder.build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 100)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.TERRAIN
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            // Draw colored segments
            mapData.segments.forEach { segment ->
                Polyline(
                    points = listOf(
                        LatLng(segment.start.lat, segment.start.lon),
                        LatLng(segment.end.lat, segment.end.lon)
                    ),
                    color = segment.severity.toColor(),
                    width = 8f
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
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Legend
        MapLegend(
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
                Text(
                    text = "Color by:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        // Events toggle
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
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
        }
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            LegendItem(color = GreenPositive, label = "Low (≤P50)")
            LegendItem(color = YellowWarning, label = "Medium")
            LegendItem(color = RedNegative, label = "High (>P75)")
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

            event.meta["peakG"]?.let { peak ->
                MetricRow(
                    label = "Peak Force",
                    value = "${String.format(Locale.US, "%.1f", (peak as Number).toFloat())} G"
                )
            }
            
            event.meta["energy300ms"]?.let { energy ->
                MetricRow(
                    label = "Impact Energy",
                    value = String.format(Locale.US, "%.2f", (energy as Number).toFloat())
                )
            }
            
            event.meta["recoveryMs"]?.let { recovery ->
                MetricRow(
                    label = "Recovery Time",
                    value = "${(recovery as Number).toInt()} ms"
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
    SegmentSeverity.LOW -> GreenPositive
    SegmentSeverity.MEDIUM -> YellowWarning
    SegmentSeverity.HIGH -> RedNegative
}
