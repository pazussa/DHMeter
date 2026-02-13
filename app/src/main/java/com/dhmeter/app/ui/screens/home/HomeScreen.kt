package com.dropindh.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.localization.AppLanguageManager
import com.dropindh.app.ui.components.NewTrackDialog
import com.dropindh.app.ui.components.PermissionHandler
import com.dropindh.app.ui.components.TrackCard
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.dhGlassCardColors
import com.dropindh.app.ui.theme.dhTopBarColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRun: (trackId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTrackDetail: (trackId: String) -> Unit,
    onNavigateToPro: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewTrackDialog by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentLanguageCode = AppLanguageManager.getSavedLanguage(context)

    PermissionHandler()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Terrain, contentDescription = null)
                        Text(
                            text = "dropIn DH",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = tr("Help", "Ayuda")
                        )
                    }
                    Box {
                        IconButton(onClick = { showLanguageMenu = true }) {
                            Text(
                                text = currentLanguageCode.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(tr("English", "Ingles")) },
                                onClick = {
                                    showLanguageMenu = false
                                    AppLanguageManager.setLanguage(
                                        context,
                                        AppLanguageManager.LANGUAGE_EN
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Spanish", "Espanol")) },
                                onClick = {
                                    showLanguageMenu = false
                                    AppLanguageManager.setLanguage(
                                        context,
                                        AppLanguageManager.LANGUAGE_ES
                                    )
                                }
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = tr("History", "Historial")
                        )
                    }
                    IconButton(onClick = onNavigateToPro) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = tr("Pro", "Pro")
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTrackDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(tr("New Track", "Nuevo track")) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HomeHeroBanner(
                trackCount = uiState.tracks.size,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )

            SensorStatusCard(
                sensorStatus = uiState.sensorStatus,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (uiState.tracks.isEmpty()) {
                EmptyTracksContent(
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = tr("Your Tracks", "Tus tracks"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(tr("Error", "Error")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(tr("OK", "Aceptar"))
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(tr("About dropIn DH", "Acerca de dropIn DH")) },
            text = {
                Text(
                    tr(
                        "dropIn DH is designed for downhill telemetry: it records your runs, compares sections, and analyzes impact, vibration, instability and speed so you can improve each descent.\n\nData & privacy: sensors and location are used for recording, including background monitoring while a run is active.\n\nYou can delete your community account from Community > Delete account.\n\nContact: dropindh@gmail.com",
                        "dropIn DH esta pensada para telemetria downhill: graba tus bajadas, compara secciones y analiza impacto, vibracion, inestabilidad y velocidad para mejorar cada descenso.\n\nDatos y privacidad: se usan sensores y ubicacion para la grabacion, incluyendo monitoreo en segundo plano mientras una bajada esta activa.\n\nPuedes eliminar tu cuenta de comunidad en Comunidad > Eliminar cuenta.\n\nContacto: dropindh@gmail.com"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(tr("OK", "Aceptar"))
                }
            }
        )
    }
}

@Composable
private fun HomeHeroBanner(
    trackCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = dhGlassCardColors(emphasis = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tr("Downhill Telemetry", "Telemetria Downhill"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tr(
                        "Rider, get the most out of every descent.",
                        "Rider, saca el maximo de tus bajadas."
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = tr(
                        "$trackCount tracks ready for analysis",
                        "$trackCount tracks listos para analisis"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        colors = dhGlassCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = tr("Sensor Status", "Estado de sensores"),
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
                text = tr("No tracks yet", "Aun no hay tracks"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tr(
                    "Create a track to start recording your downhill runs",
                    "Crea un track para empezar a grabar tus bajadas"
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

