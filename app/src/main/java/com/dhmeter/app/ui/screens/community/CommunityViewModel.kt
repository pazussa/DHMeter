package com.dropindh.app.ui.screens.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropindh.app.community.CommunityMessage
import com.dropindh.app.community.CommunityRepository
import com.dropindh.app.community.CommunityRider
import com.dropindh.app.community.CommunityUser
import com.dropindh.app.community.RiderProgress
import com.dropindh.app.monetization.EventTracker
import com.dropindh.app.monetization.MonetizationEvents
import com.dhmeter.domain.repository.RunRepository
import com.dhmeter.domain.usecase.GetTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class CommunityUiState(
    val currentUser: CommunityUser? = null,
    val riders: List<CommunityRider> = emptyList(),
    val messages: List<CommunityMessage> = emptyList(),
    val blockedUsers: Set<String> = emptySet(),
    val usernameInput: String = "",
    val locationInput: String = "",
    val messageInput: String = "",
    val selectedRider: CommunityRider? = null,
    val registrationErrorCode: String? = null,
    val messageErrorCode: String? = null,
    val moderationErrorCode: String? = null,
    val termsAccepted: Boolean = false,
    val showTermsDialog: Boolean = false,
    val showBlockedUsersDialog: Boolean = false
) {
    val requiresRegistration: Boolean get() = currentUser == null
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val getTracksUseCase: GetTracksUseCase,
    private val runRepository: RunRepository,
    private val eventTracker: EventTracker
) : ViewModel() {
    private val usernameInput = MutableStateFlow("")
    private val locationInput = MutableStateFlow("")
    private val messageInput = MutableStateFlow("")
    private val selectedRiderUsername = MutableStateFlow<String?>(null)
    private val registrationErrorCode = MutableStateFlow<String?>(null)
    private val messageErrorCode = MutableStateFlow<String?>(null)
    private val moderationErrorCode = MutableStateFlow<String?>(null)
    private val termsAccepted = MutableStateFlow(false)
    private val showTermsDialog = MutableStateFlow(false)
    private val showBlockedUsersDialog = MutableStateFlow(false)
    private val currentUserFlow = communityRepository.observeCurrentUser()
    private val blockedUsersFlow = communityRepository.observeBlockedUsers()

    private val localProgressFlow: Flow<RiderProgress> = getTracksUseCase()
        .flatMapLatest { tracks ->
            if (tracks.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(tracks.map { track -> runRepository.getRunsByTrack(track.id) }) { runsByTrack ->
                    runsByTrack.flatMap { it }
                }
            }
        }
        .map { runs ->
            val durations = runs.map { it.durationMs }.filter { it > 0L }
            val speeds = runs.mapNotNull { it.avgSpeed?.toDouble() }
            val maxSpeeds = runs.mapNotNull { it.maxSpeed?.toDouble() }
            RiderProgress(
                totalRuns = runs.size,
                bestTimeSeconds = durations.minOrNull()?.div(1000.0),
                avgSpeed = speeds.takeIf { it.isNotEmpty() }?.average(),
                maxSpeed = maxSpeeds.maxOrNull()
            )
        }

    private val ridersFlow: Flow<List<CommunityRider>> = combine(
        communityRepository.observeUsers(),
        currentUserFlow,
        localProgressFlow,
        blockedUsersFlow
    ) { users, currentUser, localProgress, blockedUsers ->
        users
            .filter { user ->
                val normalized = user.username.lowercase()
                val isCurrent = currentUser != null && user.username.equals(currentUser.username, ignoreCase = true)
                isCurrent || !blockedUsers.contains(normalized)
            }
            .map { user ->
                if (currentUser != null && user.username.equals(currentUser.username, ignoreCase = true)) {
                    CommunityRider(user = user, progress = localProgress)
                } else {
                    CommunityRider(user = user, progress = user.progress)
                }
            }
            .sortedWith(
                compareByDescending<CommunityRider> {
                    currentUser != null && it.user.username.equals(currentUser.username, ignoreCase = true)
                }.thenBy { it.user.username.lowercase() }
            )
    }

    private val messagesFlow: Flow<List<CommunityMessage>> = combine(
        communityRepository.observeMessages(),
        blockedUsersFlow
    ) { messages, blockedUsers ->
        messages.filterNot { message ->
            blockedUsers.contains(message.author.lowercase())
        }
    }

    private val baseStateFlow: Flow<BaseCommunityState> = combine(
        currentUserFlow,
        ridersFlow,
        messagesFlow,
        blockedUsersFlow
    ) { currentUser, riders, messages, blockedUsers ->
        BaseCommunityState(
            currentUser = currentUser,
            riders = riders,
            messages = messages,
            blockedUsers = blockedUsers
        )
    }

    private val inputBaseFlow: Flow<CommunityInputBaseState> = combine(
        usernameInput,
        locationInput,
        messageInput,
        selectedRiderUsername,
        registrationErrorCode
    ) { username, location, message, selectedRider, registerError ->
        CommunityInputBaseState(
            username = username,
            location = location,
            message = message,
            selectedRider = selectedRider,
            registrationErrorCode = registerError
        )
    }

    private val inputStateFlow: Flow<CommunityInputState> = combine(
        inputBaseFlow,
        termsAccepted,
        showTermsDialog,
        showBlockedUsersDialog
    ) { base, accepted, showTerms, showBlocked ->
        CommunityInputState(
            username = base.username,
            location = base.location,
            message = base.message,
            selectedRider = base.selectedRider,
            registrationErrorCode = base.registrationErrorCode,
            termsAccepted = accepted,
            showTermsDialog = showTerms,
            showBlockedUsersDialog = showBlocked
        )
    }

    val uiState: StateFlow<CommunityUiState> = combine(
        baseStateFlow,
        inputStateFlow,
        messageErrorCode,
        moderationErrorCode
    ) { baseState, inputState, chatError, moderationError ->
        CommunityUiState(
            currentUser = baseState.currentUser,
            riders = baseState.riders,
            messages = baseState.messages,
            blockedUsers = baseState.blockedUsers,
            usernameInput = inputState.username,
            locationInput = inputState.location,
            messageInput = inputState.message,
            selectedRider = baseState.riders.firstOrNull { it.user.username == inputState.selectedRider },
            registrationErrorCode = inputState.registrationErrorCode,
            messageErrorCode = chatError,
            moderationErrorCode = moderationError,
            termsAccepted = inputState.termsAccepted,
            showTermsDialog = inputState.showTermsDialog,
            showBlockedUsersDialog = inputState.showBlockedUsersDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CommunityUiState()
    )

    init {
        viewModelScope.launch {
            combine(
                currentUserFlow,
                localProgressFlow.distinctUntilChanged()
            ) { currentUser, localProgress ->
                currentUser to localProgress
            }.collect { (currentUser, localProgress) ->
                if (currentUser != null) {
                    communityRepository.syncCurrentUserProgress(localProgress)
                }
            }
        }
    }

    fun onUsernameInputChange(value: String) {
        usernameInput.value = value.take(24)
        registrationErrorCode.value = null
    }

    fun onLocationInputChange(value: String) {
        locationInput.value = value.take(36)
        registrationErrorCode.value = null
    }

    fun onMessageInputChange(value: String) {
        messageInput.value = value.take(180)
        messageErrorCode.value = null
        moderationErrorCode.value = null
    }

    fun onTermsAcceptedChange(accepted: Boolean) {
        termsAccepted.value = accepted
        if (accepted) {
            registrationErrorCode.value = null
        }
    }

    fun showTermsDialog() {
        showTermsDialog.value = true
    }

    fun dismissTermsDialog() {
        showTermsDialog.value = false
    }

    fun showBlockedUsersDialog() {
        showBlockedUsersDialog.value = true
    }

    fun dismissBlockedUsersDialog() {
        showBlockedUsersDialog.value = false
    }

    fun registerUser() {
        if (!termsAccepted.value) {
            registrationErrorCode.value = "TERMS_REQUIRED"
            return
        }
        viewModelScope.launch {
            val result = communityRepository.registerUser(
                username = usernameInput.value,
                location = locationInput.value
            )
            result.onSuccess {
                usernameInput.value = ""
                locationInput.value = ""
                registrationErrorCode.value = null
            }.onFailure { error ->
                registrationErrorCode.value = error.message ?: "REGISTER_ERROR"
            }
        }
    }

    fun sendMessage() {
        viewModelScope.launch {
            val content = messageInput.value
            val result = communityRepository.sendMessage(content)
            result.onSuccess {
                messageInput.value = ""
                messageErrorCode.value = null
            }.onFailure { error ->
                messageErrorCode.value = error.message ?: "MESSAGE_ERROR"
            }
        }
    }

    fun reportMessage(messageId: String) {
        viewModelScope.launch {
            val result = communityRepository.reportMessage(
                messageId = messageId,
                reason = "chat_message"
            )
            result.onSuccess {
                moderationErrorCode.value = null
                eventTracker.track(
                    MonetizationEvents.COMMUNITY_REPORT_SUBMITTED,
                    mapOf("target_type" to "message")
                )
            }.onFailure { error ->
                moderationErrorCode.value = error.message ?: "REPORT_ERROR"
            }
        }
    }

    fun reportUser(username: String) {
        viewModelScope.launch {
            val result = communityRepository.reportUser(
                username = username,
                reason = "user_profile"
            )
            result.onSuccess {
                moderationErrorCode.value = null
                eventTracker.track(
                    MonetizationEvents.COMMUNITY_REPORT_SUBMITTED,
                    mapOf("target_type" to "user")
                )
            }.onFailure { error ->
                moderationErrorCode.value = error.message ?: "REPORT_ERROR"
            }
        }
    }

    fun blockUser(username: String) {
        viewModelScope.launch {
            val result = communityRepository.blockUser(username)
            result.onSuccess {
                if (selectedRiderUsername.value.equals(username, ignoreCase = true)) {
                    selectedRiderUsername.value = null
                }
                moderationErrorCode.value = null
                eventTracker.track(
                    MonetizationEvents.COMMUNITY_USER_BLOCKED,
                    mapOf("target_username" to username)
                )
            }.onFailure { error ->
                moderationErrorCode.value = error.message ?: "BLOCK_ERROR"
            }
        }
    }

    fun unblockUser(username: String) {
        viewModelScope.launch {
            val result = communityRepository.unblockUser(username)
            result.onSuccess {
                moderationErrorCode.value = null
            }.onFailure { error ->
                moderationErrorCode.value = error.message ?: "UNBLOCK_ERROR"
            }
        }
    }

    fun deleteCurrentAccount() {
        viewModelScope.launch {
            moderationErrorCode.value = null
            messageErrorCode.value = null
            registrationErrorCode.value = null
            eventTracker.track(MonetizationEvents.ACCOUNT_DELETION_STARTED)
            val result = communityRepository.deleteCurrentAccount()
            result.onSuccess {
                selectedRiderUsername.value = null
                termsAccepted.value = false
                showBlockedUsersDialog.value = false
                eventTracker.track(MonetizationEvents.ACCOUNT_DELETION_COMPLETED)
            }.onFailure { error ->
                moderationErrorCode.value = error.message ?: "DELETE_ACCOUNT_ERROR"
            }
        }
    }

    fun selectRider(username: String) {
        selectedRiderUsername.value = username
    }

    fun dismissRiderDialog() {
        selectedRiderUsername.value = null
    }

    fun clearModerationError() {
        moderationErrorCode.value = null
    }
}

private data class BaseCommunityState(
    val currentUser: CommunityUser? = null,
    val riders: List<CommunityRider> = emptyList(),
    val messages: List<CommunityMessage> = emptyList(),
    val blockedUsers: Set<String> = emptySet()
)

private data class CommunityInputState(
    val username: String = "",
    val location: String = "",
    val message: String = "",
    val selectedRider: String? = null,
    val registrationErrorCode: String? = null,
    val termsAccepted: Boolean = false,
    val showTermsDialog: Boolean = false,
    val showBlockedUsersDialog: Boolean = false
)

private data class CommunityInputBaseState(
    val username: String = "",
    val location: String = "",
    val message: String = "",
    val selectedRider: String? = null,
    val registrationErrorCode: String? = null
)

