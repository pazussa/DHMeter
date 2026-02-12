package com.dhmeter.app.ui.screens.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.community.CommunityMessage
import com.dhmeter.app.community.CommunityRider
import com.dhmeter.app.ui.i18n.tr
import com.dhmeter.app.ui.theme.dhGlassCardColors
import com.dhmeter.app.ui.theme.dhTopBarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    onBack: () -> Unit,
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null)
                        Text(
                            text = tr("Community", "Comunidad"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = tr("Back", "Atras")
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.requiresRegistration) {
            RegistrationContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                username = uiState.usernameInput,
                location = uiState.locationInput,
                errorCode = uiState.registrationErrorCode,
                onUsernameChange = viewModel::onUsernameInputChange,
                onLocationChange = viewModel::onLocationInputChange,
                onRegister = viewModel::registerUser
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CurrentUserCard(
                        username = uiState.currentUser?.username.orEmpty(),
                        location = uiState.currentUser?.location.orEmpty()
                    )
                }
                item {
                    RidersCard(
                        riders = uiState.riders,
                        onSelectRider = viewModel::selectRider
                    )
                }
                item {
                    GroupChatCard(
                        messages = uiState.messages,
                        myUsername = uiState.currentUser?.username.orEmpty(),
                        messageInput = uiState.messageInput,
                        messageErrorCode = uiState.messageErrorCode,
                        onMessageChange = viewModel::onMessageInputChange,
                        onSend = viewModel::sendMessage
                    )
                }
            }
        }
    }

    uiState.selectedRider?.let { rider ->
        RiderProgressDialog(
            rider = rider,
            onDismiss = viewModel::dismissRiderDialog
        )
    }
}

@Composable
private fun RegistrationContent(
    modifier: Modifier = Modifier,
    username: String,
    location: String,
    errorCode: String?,
    onUsernameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onRegister: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = dhGlassCardColors(emphasis = true)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = tr(
                        "Create your rider profile",
                        "Crea tu perfil de rider"
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = tr(
                        "Use a unique username and your city to join the community chat.",
                        "Usa un nombre unico y tu ciudad para entrar al chat de comunidad."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("Username", "Usuario")) }
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr("City or place", "Ciudad o lugar")) }
                )
                mapErrorCode(errorCode)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Enter community", "Entrar a comunidad"))
                }
            }
        }
    }
}

@Composable
private fun CurrentUserCard(
    username: String,
    location: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors(emphasis = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terrain,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = tr("Logged in as", "Conectado como"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$username - $location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RidersCard(
    riders: List<CommunityRider>,
    onSelectRider: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr("Riders in community", "Riders en la comunidad"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = tr(
                    "Tap a rider to view basic progress.",
                    "Toca un rider para ver su progreso basico."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            riders.forEach { rider ->
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectRider(rider.user.username) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rider.user.username,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rider.user.location,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatCard(
    messages: List<CommunityMessage>,
    myUsername: String,
    messageInput: String,
    messageErrorCode: String?,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tr("Group chat", "Chat grupal"),
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isMine = message.author.equals(myUsername, ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMine) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = message.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatTime(message.sentAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(tr("Write message", "Escribe mensaje")) }
                )
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = tr("Send", "Enviar")
                    )
                }
            }
            mapErrorCode(messageErrorCode)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RiderProgressDialog(
    rider: CommunityRider,
    onDismiss: () -> Unit
) {
    val progress = rider.progress
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${rider.user.username} - ${rider.user.location}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Basic progress", "Progreso basico"), fontWeight = FontWeight.Bold)
                Text("${tr("Runs", "Bajadas")}: ${progress.totalRuns}")
                Text("${tr("Best time", "Mejor tiempo")}: ${formatMetric(progress.bestTimeSeconds, "s")}")
                Text("${tr("Average speed", "Velocidad promedio")}: ${formatMetric(progress.avgSpeed, "")}")
                Text("${tr("Max speed", "Velocidad maxima")}: ${formatMetric(progress.maxSpeed, "")}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "Cerrar"))
            }
        }
    )
}

@Composable
private fun mapErrorCode(code: String?): String? {
    return when (code) {
        "USERNAME_REQUIRED" -> tr("Username is required.", "El usuario es obligatorio.")
        "LOCATION_REQUIRED" -> tr("City or place is required.", "La ciudad o lugar es obligatoria.")
        "USERNAME_TAKEN" -> tr("That username is already in use.", "Ese usuario ya esta en uso.")
        "USER_NOT_REGISTERED" -> tr("Register first to send messages.", "Registrate primero para enviar mensajes.")
        "MESSAGE_EMPTY" -> tr("Message cannot be empty.", "El mensaje no puede ir vacio.")
        null -> null
        else -> tr("Unexpected error.", "Error inesperado.")
    }
}

private fun formatMetric(value: Double?, suffix: String): String {
    return if (value == null) {
        "-"
    } else {
        val formatted = String.format(Locale.US, "%.2f", value)
        if (suffix.isBlank()) formatted else "$formatted $suffix"
    }
}

private fun formatTime(epochMillis: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(epochMillis))
}
