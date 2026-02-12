package com.dhmeter.app.ui.screens.trackdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.ui.metrics.formatScore0to100
import com.dhmeter.app.ui.metrics.runOverallQualityScore
import com.dhmeter.domain.model.Run
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    trackId: String,
    onRunSelected: (runId: String) -> Unit,
    onCompareRuns: (runIds: List<String>) -> Unit,
    onStartNewRun: () -> Unit,
    onBack: () -> Unit,
    viewModel: TrackDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedRuns by remember { mutableStateOf(setOf<String>()) }
    var isCompareMode by remember { mutableStateOf(false) }

    LaunchedEffect(trackId) {
        viewModel.loadTrack(trackId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(uiState.track?.name ?: "Track Details")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.runs.size >= 2) {
                        IconButton(
                            onClick = { 
                                isCompareMode = !isCompareMode
                                if (!isCompareMode) selectedRuns = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = if (isCompareMode) Icons.Default.Close else Icons.Default.Compare,
                                contentDescription = "Compare mode"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isCompareMode) {
                FloatingActionButton(
                    onClick = onStartNewRun,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start new run")
                }
            } else if (selectedRuns.size >= 2) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onCompareRuns(selectedRuns.toList())
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Compare, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compare ${selectedRuns.size}")
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
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Track info
                    uiState.track?.let { track ->
                        TrackInfoHeader(
                            track = track,
                            runCount = uiState.runs.size,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    // Compare mode instructions
                    if (isCompareMode) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = "Select 2+ runs to compare (${selectedRuns.size} selected)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Runs list
                    if (uiState.runs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBike,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No runs yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Start your first run on this track",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.runs, key = { it.runId }) { run ->
                                RunCard(
                                    run = run,
                                    isSelected = selectedRuns.contains(run.runId),
                                    isCompareMode = isCompareMode,
                                    onSelect = {
                                        if (isCompareMode) {
                                            selectedRuns = if (selectedRuns.contains(run.runId)) {
                                                selectedRuns - run.runId
                                            } else {
                                                selectedRuns + run.runId
                                            }
                                        } else {
                                            onRunSelected(run.runId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun TrackInfoHeader(
    track: com.dhmeter.domain.model.Track,
    runCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terrain,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                track.locationHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                track.notes?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = runCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "runs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunCard(
    run: Run,
    isSelected: Boolean,
    isCompareMode: Boolean,
    onSelect: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val overallQuality = runOverallQualityScore(run)
    
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCompareMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Icon(
                imageVector = Icons.Default.DirectionsBike,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dateFormat.format(Date(run.startedAt)),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Duration: ${formatDuration(run.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Quick metrics (shown for all runs)
            Column(
                horizontalAlignment = Alignment.End
            ) {
                overallQuality?.let {
                    Text(
                        text = "Quality: ${formatScore0to100(it)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                run.avgSpeed?.let { speed ->
                    Text(
                        text = "${String.format(Locale.US, "%.1f", speed * 3.6f)} km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}

