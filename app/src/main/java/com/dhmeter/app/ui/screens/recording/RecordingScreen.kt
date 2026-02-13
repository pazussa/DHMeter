package com.dropindh.app.ui.screens.recording

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.domain.model.SensorSensitivitySettings
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.RedNegative
import com.dropindh.app.ui.theme.dhGlassCardColors
import com.dropindh.app.ui.theme.dhTopBarColors
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
    var showSensitivityPanel by remember { mutableStateOf(false) }

    LaunchedEffect(trackId) {
        viewModel.initialize(trackId)
    }

    LaunchedEffect(uiState.completedRunId) {
        uiState.completedRunId?.let { runId ->
            viewModel.consumeCompletedRun()
            onRunCompleted(runId)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isRecording) {
                            RecordingDot()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("REC", "REC"))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = formatDuration(uiState.elapsedSeconds),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(tr("Recording", "Grabando"))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        enabled = !uiState.isRecording &&
                                !uiState.isProcessing &&
                                uiState.manualStartCountdownSeconds == 0
                    ) {
                        Icon(Icons.Default.Close, contentDescription = tr("Cancel", "Cancelar"))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSensitivityPanel = true },
                        enabled = !uiState.isProcessing
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = tr("Sensor sensitivity", "Sensibilidad de sensores"))
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
                text = tr("Phone: Pocket (thigh)", "Telefono: Bolsillo (muslo)"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            AutoSegmentsCard(
                segmentCount = uiState.segmentCount,
                status = uiState.segmentStatus,
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
                    colors = dhGlassCardColors()
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
                    Text(tr("STOP", "DETENER"))
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
                    if (uiState.manualStartCountdownSeconds > 0) {
                        Text(
                            tr(
                                "STARTING IN ${uiState.manualStartCountdownSeconds}s",
                                "INICIANDO EN ${uiState.manualStartCountdownSeconds}s"
                            )
                        )
                    } else {
                        Text(tr("START RUN", "INICIAR BAJADA"))
                    }
                }
            }

            if (uiState.isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tr("Processing run data...", "Procesando datos de la bajada..."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (uiState.canRecoverFromProcessing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.resetStuckProcessing() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr("Reset processing", "Reiniciar procesamiento"))
                    }
                }
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text(tr("Stop Recording?", "Detener grabacion?")) },
            text = { Text(tr("Are you sure you want to stop recording this run?", "Seguro que quieres detener esta grabacion?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmation = false
                        viewModel.stopRecording()
                    }
                ) {
                    Text(tr("Stop", "Detener"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text(tr("Continue Recording", "Continuar grabando"))
                }
            }
        )
    }

    if (showSensitivityPanel) {
        SensorSensitivitySheet(
            settings = uiState.sensitivitySettings,
            onDismiss = { showSensitivityPanel = false },
            onImpactSensitivityChange = viewModel::updateImpactSensitivity,
            onHarshnessSensitivityChange = viewModel::updateHarshnessSensitivity,
            onStabilitySensitivityChange = viewModel::updateStabilitySensitivity,
            onGpsSensitivityChange = viewModel::updateGpsSensitivity,
            onResetDefaults = viewModel::resetSensitivityDefaults
        )
    }

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(tr("Recording Error", "Error de grabacion")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(tr("OK", "Aceptar"))
                }
            }
        )
    }
}

@Composable
private fun AutoSegmentsCard(
    segmentCount: Int,
    status: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = dhGlassCardColors(emphasis = true)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("Local Segments: $segmentCount", "Segmentos locales: $segmentCount"),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (segmentCount > 0) {
                Text(
                    text = tr(
                        "Auto-start triggers near start, above ~9 km/h, and aligned direction.",
                        "El auto-inicio se activa cerca del inicio, sobre ~9 km/h y con direccion alineada."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
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
private fun LiveMetricsBarsCard(
    impactLevel: Float,
    harshnessLevel: Float,
    stabilityLevel: Float,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = dhGlassCardColors(emphasis = isPreview)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPreview) {
                Text(
                    text = tr("Sensor Preview", "Vista previa de sensores"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            LiveMetricBar(
                label = if (isPreview) tr("Impacts", "Impactos") else tr("Impacts (live)", "Impactos (en vivo)"),
                level = impactLevel,
                color = Color(0xFFE91E63) // Pink for impacts
            )
            
            LiveMetricBar(
                label = if (isPreview) tr("Vibration", "Vibracion") else tr("Vibration (live)", "Vibracion (en vivo)"),
                level = harshnessLevel,
                color = Color(0xFFFF9800) // Orange for harshness
            )
            
            LiveMetricBar(
                label = if (isPreview) tr("Instability", "Inestabilidad") else tr("Instability (live)", "Inestabilidad (en vivo)"),
                level = stabilityLevel,
                color = Color(0xFFD32F2F)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorSensitivitySheet(
    settings: SensorSensitivitySettings,
    onDismiss: () -> Unit,
    onImpactSensitivityChange: (Float) -> Unit,
    onHarshnessSensitivityChange: (Float) -> Unit,
    onStabilitySensitivityChange: (Float) -> Unit,
    onGpsSensitivityChange: (Float) -> Unit,
    onResetDefaults: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
        sheetMaxWidth = Dp.Unspecified
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = tr("Sensor Sensitivity", "Sensibilidad de sensores"),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = tr(
                    "Changes apply to preview and new recordings.",
                    "Los cambios se aplican a la vista previa y nuevas grabaciones."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            SensitivitySliderRow(
                label = tr("Impact (Accelerometer)", "Impacto (Acelerometro)"),
                value = settings.impactSensitivity,
                onValueChange = onImpactSensitivityChange
            )
            SensitivitySliderRow(
                label = tr("Vibration (Accelerometer)", "Vibracion (Acelerometro)"),
                value = settings.harshnessSensitivity,
                onValueChange = onHarshnessSensitivityChange
            )
            SensitivitySliderRow(
                label = tr("Instability (Gyroscope)", "Inestabilidad (Giroscopio)"),
                value = settings.stabilitySensitivity,
                onValueChange = onStabilitySensitivityChange
            )
            SensitivitySliderRow(
                label = "GPS",
                value = settings.gpsSensitivity,
                onValueChange = onGpsSensitivityChange
            )

            TextButton(
                onClick = onResetDefaults,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(tr("Reset defaults", "Restaurar valores"))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SensitivitySliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = SensorSensitivitySettings.MIN_SENSITIVITY..SensorSensitivitySettings.MAX_SENSITIVITY,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, secs)
}

enum class GpsSignalLevel { NONE, POOR, MEDIUM, GOOD }
enum class SignalQuality { UNKNOWN, LOOSE, MODERATE, STABLE }


