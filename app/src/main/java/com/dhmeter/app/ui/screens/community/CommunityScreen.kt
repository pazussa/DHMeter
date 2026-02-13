package com.dropindh.app.ui.screens.community

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropindh.app.community.CommunityMessage
import com.dropindh.app.community.CommunityRider
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.dhGlassCardColors
import com.dropindh.app.ui.theme.dhTopBarColors
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
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                            contentDescription = tr("Back", "Atrás")
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
                termsAccepted = uiState.termsAccepted,
                errorCode = uiState.registrationErrorCode,
                onUsernameChange = viewModel::onUsernameInputChange,
                onLocationChange = viewModel::onLocationInputChange,
                onTermsAcceptedChange = viewModel::onTermsAcceptedChange,
                onShowTerms = viewModel::showTermsDialog,
                onRegister = viewModel::registerUser
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CurrentUserCard(
                        username = uiState.currentUser?.username.orEmpty(),
                        location = uiState.currentUser?.location.orEmpty(),
                        blockedCount = uiState.blockedUsers.size,
                        onShowBlockedUsers = viewModel::showBlockedUsersDialog,
                        onDeleteAccount = { showDeleteConfirm = true }
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
                        onSend = viewModel::sendMessage,
                        onReportMessage = viewModel::reportMessage,
                        onBlockUser = viewModel::blockUser
                    )
                }
            }
        }
    }

    uiState.selectedRider?.let { rider ->
        RiderProgressDialog(
            rider = rider,
            isCurrentUser = uiState.currentUser?.username.equals(rider.user.username, ignoreCase = true),
            onDismiss = viewModel::dismissRiderDialog,
            onReportUser = { viewModel.reportUser(rider.user.username) },
            onBlockUser = { viewModel.blockUser(rider.user.username) }
        )
    }

    if (uiState.showTermsDialog) {
        TermsDialog(onDismiss = viewModel::dismissTermsDialog)
    }

    if (uiState.showBlockedUsersDialog) {
        BlockedUsersDialog(
            blockedUsers = uiState.blockedUsers.toList().sorted(),
            onDismiss = viewModel::dismissBlockedUsersDialog,
            onUnblockUser = viewModel::unblockUser
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(tr("Delete account", "Eliminar cuenta")) },
            text = {
                Text(
                    tr(
                        "This removes your community profile and your chat messages from the cloud.",
                        "Esto elimina tu perfil de comunidad y tus mensajes del chat en la nube."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCurrentAccount()
                    }
                ) {
                    Text(tr("Delete", "Eliminar"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(tr("Cancel", "Cancelar"))
                }
            }
        )
    }

    mapErrorCode(uiState.moderationErrorCode)?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearModerationError,
            title = { Text(tr("Community moderation", "Moderacion de comunidad")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearModerationError) {
                    Text(tr("OK", "Aceptar"))
                }
            }
        )
    }
}

@Composable
private fun RegistrationContent(
    modifier: Modifier = Modifier,
    username: String,
    location: String,
    termsAccepted: Boolean,
    errorCode: String?,
    onUsernameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onShowTerms: () -> Unit,
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
                        "Usa un nombre único y tu ciudad para entrar al chat de comunidad."
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = onTermsAcceptedChange
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(
                                "I accept the community rules and terms.",
                                "Acepto las reglas y términos de la comunidad."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onShowTerms) {
                            Text(tr("Read terms", "Leer términos"))
                        }
                    }
                }
                mapErrorCode(errorCode)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = termsAccepted
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
    location: String,
    blockedCount: Int,
    onShowBlockedUsers: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors(emphasis = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onShowBlockedUsers
                ) {
                    Text(
                        tr(
                            "Blocked ($blockedCount)",
                            "Bloqueados ($blockedCount)"
                        )
                    )
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDeleteAccount
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("Delete account", "Eliminar cuenta"))
                }
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
                    "Tap a rider to view basic progress or moderation actions.",
                    "Toca un rider para ver progreso básico o acciones de moderación."
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
    onSend: () -> Unit,
    onReportMessage: (String) -> Unit,
    onBlockUser: (String) -> Unit
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
                    var showMenu by remember(message.id) { mutableStateOf(false) }
                    val isMine = message.author.equals(myUsername, ignoreCase = true)
                    val canModerateMessage = !isMine && !message.author.equals("system", ignoreCase = true)

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
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = message.author,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (canModerateMessage) {
                                        IconButton(
                                            modifier = Modifier.size(20.dp),
                                            onClick = { showMenu = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = tr("Moderate", "Moderar"),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(tr("Report message", "Reportar mensaje")) },
                                                onClick = {
                                                    showMenu = false
                                                    onReportMessage(message.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(tr("Block user", "Bloquear usuario")) },
                                                onClick = {
                                                    showMenu = false
                                                    onBlockUser(message.author)
                                                }
                                            )
                                        }
                                    }
                                }
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
    isCurrentUser: Boolean,
    onDismiss: () -> Unit,
    onReportUser: () -> Unit,
    onBlockUser: () -> Unit
) {
    val progress = rider.progress
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${rider.user.username} - ${rider.user.location}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Basic progress", "Progreso básico"), fontWeight = FontWeight.Bold)
                Text("${tr("Runs", "Bajadas")}: ${progress.totalRuns}")
                Text("${tr("Best time", "Mejor tiempo")}: ${formatMetric(progress.bestTimeSeconds, "s")}")
                Text("${tr("Average speed", "Velocidad promedio")}: ${formatMetric(progress.avgSpeed, "")}")
                Text("${tr("Max speed", "Velocidad máxima")}: ${formatMetric(progress.maxSpeed, "")}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "Cerrar"))
            }
        },
        dismissButton = {
            if (!isCurrentUser) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onReportUser) {
                        Text(tr("Report", "Reportar"))
                    }
                    TextButton(onClick = onBlockUser) {
                        Text(tr("Block", "Bloquear"))
                    }
                }
            }
        }
    )
}

@Composable
private fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Community terms", "Terminos de comunidad")) },
        text = {
            Text(
                tr(
                    "Respect riders, avoid offensive content, do not share unsafe instructions, and report abusive behavior. By joining the community chat you accept moderation actions such as report and block.",
                    "Respeta a los riders, evita contenido ofensivo, no compartas instrucciones inseguras y reporta conductas abusivas. Al entrar al chat aceptas acciones de moderación como reporte y bloqueo."
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("OK", "Aceptar"))
            }
        }
    )
}

@Composable
private fun BlockedUsersDialog(
    blockedUsers: List<String>,
    onDismiss: () -> Unit,
    onUnblockUser: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Blocked users", "Usuarios bloqueados")) },
        text = {
            if (blockedUsers.isEmpty()) {
                Text(tr("You have no blocked users.", "No tienes usuarios bloqueados."))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    blockedUsers.forEach { username ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(username, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUnblockUser(username) }) {
                                Text(tr("Unblock", "Desbloquear"))
                            }
                        }
                    }
                }
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
        "USERNAME_TAKEN" -> tr("That username is already in use.", "Ese usuario ya está en uso.")
        "TERMS_REQUIRED" -> tr("You must accept terms to continue.", "Debes aceptar los términos para continuar.")
        "USER_NOT_REGISTERED" -> tr("Register first to continue.", "Registrate primero para continuar.")
        "MESSAGE_EMPTY" -> tr("Message cannot be empty.", "El mensaje no puede ir vacio.")
        "MESSAGE_NOT_FOUND" -> tr("Message was not found.", "No se encontro el mensaje.")
        "REPORT_TARGET_REQUIRED" -> tr("Choose a user to report.", "Selecciona un usuario para reportar.")
        "REPORT_TARGET_NOT_FOUND" -> tr("User was not found.", "No se encontro el usuario.")
        "BLOCK_TARGET_REQUIRED" -> tr("Choose a user to block.", "Selecciona un usuario para bloquear.")
        "BLOCK_TARGET_NOT_FOUND" -> tr("User was not found.", "No se encontro el usuario.")
        "BLOCK_SELF_NOT_ALLOWED" -> tr("You cannot block your own account.", "No puedes bloquear tu propia cuenta.")
        "CLOUD_NOT_CONFIGURED" -> tr(
            "Cloud community is not configured yet.",
            "La comunidad en nube aún no está configurada."
        )
        "CLOUD_AUTH_FAILED" -> tr(
            "Cloud authentication failed.",
            "Fallo la autenticacion en la nube."
        )
        "CLOUD_UNAVAILABLE" -> tr(
            "Cloud service unavailable. Try again.",
            "Servicio en la nube no disponible. Intenta de nuevo."
        )
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

