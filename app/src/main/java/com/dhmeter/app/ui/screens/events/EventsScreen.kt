package com.dropindh.app.ui.screens.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.*
import com.dhmeter.domain.model.RunEvent
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    runId: String,
    onBack: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(runId) {
        viewModel.loadEvents(runId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = { Text(tr("Events", "Eventos")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "Atrás"))
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
            uiState.events.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = tr("No events detected", "No se detectaron eventos"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Summary header
                    item {
                        EventsSummary(events = uiState.events)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Events by type
                    items(uiState.events.sortedBy { it.distPct }) { event ->
                        EventCard(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsSummary(events: List<RunEvent>) {
    val landings = events.count { it.type == "LANDING" }
    val impacts = events.count { it.type == "IMPACT_PEAK" }
    val harshnessBursts = events.count { it.type == "HARSHNESS_BURST" }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors(emphasis = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = tr("Summary", "Resumen"),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    icon = Icons.Default.FlightLand,
                    count = landings,
                    label = tr("Landings", "Aterrizajes")
                )
                SummaryItem(
                    icon = Icons.Default.Bolt,
                    count = impacts,
                    label = tr("Impacts", "Impactos")
                )
                SummaryItem(
                    icon = Icons.Default.Vibration,
                    count = harshnessBursts,
                    label = tr("Harshness", "Vibración")
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    count: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EventCard(event: RunEvent) {
    val (icon, color, label) = when (event.type) {
        "LANDING" -> Triple(Icons.Default.FlightLand, ChartImpact, tr("Landing", "Aterrizaje"))
        "IMPACT_PEAK" -> Triple(Icons.Default.Bolt, ChartHarshness, tr("Impact Peak", "Pico de impacto"))
        "HARSHNESS_BURST" -> Triple(Icons.Default.Vibration, YellowWarning, tr("Harshness Burst", "Ráfaga de vibración"))
        else -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.outline, event.type)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = tr(
                        "At ${String.format(Locale.US, "%.1f", event.distPct)}% of track",
                        "En ${String.format(Locale.US, "%.1f", event.distPct)}% del track"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = tr(
                        "Time: ${String.format(Locale.US, "%.1f", event.timeSec)}s",
                        "Tiempo: ${String.format(Locale.US, "%.1f", event.timeSec)}s"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = tr("Severity", "Severidad"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = String.format(Locale.US, "%.1f", event.severity),
                    style = MaterialTheme.typography.titleMedium,
                    color = getSeverityColor(event.severity)
                )
            }
        }
    }
}

@Composable
private fun getSeverityColor(severity: Float): androidx.compose.ui.graphics.Color {
    return when {
        severity < 2f -> GreenPositive
        severity < 4f -> YellowWarning
        else -> RedNegative
    }
}


