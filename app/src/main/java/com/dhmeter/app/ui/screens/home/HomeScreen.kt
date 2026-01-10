package com.dhmeter.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.ui.components.TrackCard
import com.dhmeter.app.ui.components.NewTrackDialog
import com.dhmeter.app.ui.components.PermissionHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRun: (trackId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTrackDetail: (trackId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewTrackDialog by remember { mutableStateOf(false) }

    PermissionHandler()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "DH Meter",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTrackDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Track") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sensor status card
            SensorStatusCard(
                sensorStatus = uiState.sensorStatus,
                modifier = Modifier.padding(16.dp)
            )
            
            // Include invalid runs toggle
            IncludeInvalidRunsToggle(
                checked = uiState.includeInvalidRuns,
                onCheckedChange = { viewModel.setIncludeInvalidRuns(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Tracks section
            if (uiState.tracks.isEmpty()) {
                EmptyTracksContent(
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "Your Tracks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.tracks, key = { it.id }) { track ->
                        TrackCard(
                            track = track,
                            onStartRun = { onStartRun(track.id) },
                            onViewDetail = { onNavigateToTrackDetail(track.id) }
                        )
                    }
                }
            }
        }
    }

    if (showNewTrackDialog) {
        NewTrackDialog(
            onDismiss = { showNewTrackDialog = false },
            onConfirm = { name, locationHint ->
                viewModel.createTrack(name, locationHint)
                showNewTrackDialog = false
            }
        )
    }
}

@Composable
private fun IncludeInvalidRunsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) 
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Include invalid runs",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Show in charts & comparisons",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SensorStatusCard(
    sensorStatus: SensorStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sensor Status",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SensorIndicator(
                    name = "LinAcc",
                    isAvailable = sensorStatus.hasAccelerometer,
                    isRequired = true
                )
                SensorIndicator(
                    name = "Gyro",
                    isAvailable = sensorStatus.hasGyroscope,
                    isRequired = true
                )
                SensorIndicator(
                    name = "RotVec",
                    isAvailable = sensorStatus.hasRotationVector,
                    isRequired = true
                )
                SensorIndicator(
                    name = "GPS",
                    isAvailable = sensorStatus.hasGps,
                    isRequired = true
                )
            }
        }
    }
}

@Composable
private fun SensorIndicator(
    name: String,
    isAvailable: Boolean,
    isRequired: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = when {
                isAvailable -> MaterialTheme.colorScheme.primary
                isRequired -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyTracksContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terrain,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No tracks yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create a track to start recording your downhill runs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}
