package com.dhmeter.app.ui.screens.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.ui.theme.GreenPositive
import com.dhmeter.app.ui.theme.RedNegative
import com.dhmeter.app.ui.theme.YellowWarning
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    trackId: String,
    onRunCompleted: (runId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showStopConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(trackId) {
        viewModel.initialize(trackId)
    }

    LaunchedEffect(uiState.completedRunId) {
        uiState.completedRunId?.let { runId ->
            onRunCompleted(runId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isRecording) {
                            RecordingDot()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("REC")
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = formatDuration(uiState.elapsedSeconds),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text("Recording")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !uiState.isRecording) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track info
            Text(
                text = uiState.trackName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Phone: Pocket (thigh)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status indicators card (Signal, GPS, Movement)
            StatusIndicatorsCard(
                gpsSignal = uiState.gpsSignal,
                gpsAccuracyText = when (uiState.gpsSignal) {
                    GpsSignalLevel.GOOD -> "OK (+/-5m)"
                    GpsSignalLevel.MEDIUM -> "OK (+/-15m)"
                    GpsSignalLevel.POOR -> "Poor (+/-25m+)"
                    GpsSignalLevel.NONE -> "No signal"
                },
                movementDetected = uiState.movementDetected,
                signalQuality = uiState.signalQuality,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live metrics bars (shown when recording OR as preview before start)
            LiveMetricsBarsCard(
                impactLevel = uiState.liveImpact,
                harshnessLevel = uiState.liveHarshness,
                stabilityLevel = uiState.liveStability,
                isPreview = !uiState.isRecording,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (uiState.isRecording) {
                // Speed display
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
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f km/h", uiState.currentSpeed * 3.6f),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Control buttons
            if (uiState.isRecording) {
                Button(
                    onClick = { showStopConfirmation = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("STOP")
                }
            } else {
                Button(
                    onClick = { viewModel.startRecording() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = uiState.canStartRecording
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START RUN")
                }
            }

            if (uiState.isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Processing run data...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop Recording?") },
            text = { Text("Are you sure you want to stop recording this run?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmation = false
                        viewModel.stopRecording()
                    }
                ) {
                    Text("Stop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("Continue Recording")
                }
            }
        )
    }

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Recording Error") },
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
private fun RecordingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(RedNegative.copy(alpha = alpha))
    )
}

@Composable
private fun RecordingStatusIndicator(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isRecording) {
                    RedNegative.copy(alpha = alpha * 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) RedNegative else MaterialTheme.colorScheme.primary
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun StatusIndicatorsCard(
    gpsSignal: GpsSignalLevel,
    gpsAccuracyText: String,
    movementDetected: Boolean,
    signalQuality: SignalQuality,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Signal quality
            StatusRow(
                label = "Signal:",
                statusText = signalQuality.name,
                statusColor = when (signalQuality) {
                    SignalQuality.STABLE -> GreenPositive
                    SignalQuality.MODERATE -> YellowWarning
                    SignalQuality.LOOSE -> RedNegative
                    SignalQuality.UNKNOWN -> MaterialTheme.colorScheme.outline
                }
            )
            
            // GPS
            StatusRow(
                label = "GPS:",
                statusText = gpsAccuracyText,
                statusColor = when (gpsSignal) {
                    GpsSignalLevel.GOOD -> GreenPositive
                    GpsSignalLevel.MEDIUM -> YellowWarning
                    GpsSignalLevel.POOR -> RedNegative
                    GpsSignalLevel.NONE -> MaterialTheme.colorScheme.outline
                }
            )
            
            // Movement
            StatusRow(
                label = "Movement:",
                statusText = if (movementDetected) "CONTINUOUS" else "WAITING",
                statusColor = if (movementDetected) GreenPositive else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
        }
    }
}

@Composable
private fun LiveMetricsBarsCard(
    impactLevel: Float,
    harshnessLevel: Float,
    stabilityLevel: Float,
    isPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPreview) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPreview) {
                Text(
                    text = "Sensor Preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            LiveMetricBar(
                label = if (isPreview) "Impacts" else "Impacts (live)",
                level = impactLevel,
                color = Color(0xFFE91E63) // Pink for impacts
            )
            
            LiveMetricBar(
                label = if (isPreview) "Vibration" else "Vibration (live)",
                level = harshnessLevel,
                color = Color(0xFFFF9800) // Orange for harshness
            )
            
            LiveMetricBar(
                label = "Stability",
                level = 1f - stabilityLevel, // Invert: higher bar = more stable
                color = Color(0xFF4CAF50) // Green for stability
            )
        }
    }
}

@Composable
private fun LiveMetricBar(
    label: String,
    level: Float,
    color: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        // Bar background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Filled portion with animation
            val animatedLevel by animateFloatAsState(
                targetValue = level.coerceIn(0f, 1f),
                animationSpec = tween(150),
                label = "bar_level"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedLevel)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            
            // Bar segments overlay (visual guide)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, secs)
}

enum class GpsSignalLevel { NONE, POOR, MEDIUM, GOOD }
enum class SignalQuality { UNKNOWN, LOOSE, MODERATE, STABLE }

