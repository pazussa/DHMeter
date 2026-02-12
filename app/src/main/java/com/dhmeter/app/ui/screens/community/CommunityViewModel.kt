package com.dhmeter.app.ui.screens.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.app.community.CommunityMessage
import com.dhmeter.app.community.CommunityRepository
import com.dhmeter.app.community.CommunityRider
import com.dhmeter.app.community.CommunityUser
import com.dhmeter.app.community.RiderProgress
import com.dhmeter.domain.repository.RunRepository
import com.dhmeter.domain.usecase.GetTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

data class CommunityUiState(
    val currentUser: CommunityUser? = null,
    val riders: List<CommunityRider> = emptyList(),
    val messages: List<CommunityMessage> = emptyList(),
    val usernameInput: String = "",
    val locationInput: String = "",
    val messageInput: String = "",
    val selectedRider: CommunityRider? = null,
    val registrationErrorCode: String? = null,
    val messageErrorCode: String? = null
) {
    val requiresRegistration: Boolean get() = currentUser == null
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
    private val getTracksUseCase: GetTracksUseCase,
    private val runRepository: RunRepository
) : ViewModel() {
    private val usernameInput = MutableStateFlow("")
    private val locationInput = MutableStateFlow("")
    private val messageInput = MutableStateFlow("")
    private val selectedRiderUsername = MutableStateFlow<String?>(null)
    private val registrationErrorCode = MutableStateFlow<String?>(null)
    private val messageErrorCode = MutableStateFlow<String?>(null)
    private val currentUserFlow = communityRepository.observeCurrentUser()

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
            val durations = runs.map { it.durationMs }
                .filter { it > 0L }
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
        localProgressFlow
    ) { users, currentUser, localProgress ->
        users
            .map { user ->
                if (currentUser != null && user.username.equals(currentUser.username, ignoreCase = true)) {
                    CommunityRider(user = user, progress = localProgress)
                } else {
                    CommunityRider(user = user, progress = demoProgressFor(user.username))
                }
            }
            .sortedWith(
                compareByDescending<CommunityRider> {
                    currentUser != null && it.user.username.equals(currentUser.username, ignoreCase = true)
                }.thenBy { it.user.username.lowercase() }
            )
    }

    private val baseStateFlow: Flow<BaseCommunityState> = combine(
        currentUserFlow,
        ridersFlow,
        communityRepository.observeMessages()
    ) { currentUser, riders, messages ->
        BaseCommunityState(
            currentUser = currentUser,
            riders = riders,
            messages = messages
        )
    }

    private val inputStateFlow: Flow<CommunityInputState> = combine(
        usernameInput,
        locationInput,
        messageInput,
        selectedRiderUsername,
        registrationErrorCode
    ) { username, location, message, selectedRider, registerError ->
        CommunityInputState(
            username = username,
            location = location,
            message = message,
            selectedRider = selectedRider,
            registrationErrorCode = registerError
        )
    }

    val uiState: StateFlow<CommunityUiState> = combine(
        baseStateFlow,
        inputStateFlow,
        messageErrorCode
    ) { baseState, inputState, chatError ->
        CommunityUiState(
            currentUser = baseState.currentUser,
            riders = baseState.riders,
            messages = baseState.messages,
            usernameInput = inputState.username,
            locationInput = inputState.location,
            messageInput = inputState.message,
            selectedRider = baseState.riders.firstOrNull { it.user.username == inputState.selectedRider },
            registrationErrorCode = inputState.registrationErrorCode,
            messageErrorCode = chatError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CommunityUiState()
    )

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
    }

    fun registerUser() {
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
                postDemoReplyDelayed()
            }.onFailure { error ->
                messageErrorCode.value = error.message ?: "MESSAGE_ERROR"
            }
        }
    }

    fun selectRider(username: String) {
        selectedRiderUsername.value = username
    }

    fun dismissRiderDialog() {
        selectedRiderUsername.value = null
    }

    private fun postDemoReplyDelayed() {
        viewModelScope.launch {
            delay(900L)
            val phrase = demoReplies[(System.currentTimeMillis() % demoReplies.size).toInt()]
            communityRepository.addDemoReply(phrase)
        }
    }

    private fun demoProgressFor(username: String): RiderProgress {
        val seed = abs(username.hashCode())
        val runs = 4 + (seed % 24)
        val best = 42.0 + (seed % 310) / 10.0
        val avg = 21.0 + (seed % 180) / 10.0
        val max = avg + 4.0 + (seed % 80) / 10.0
        return RiderProgress(
            totalRuns = runs,
            bestTimeSeconds = best,
            avgSpeed = avg,
            maxSpeed = max
        )
    }

    private companion object {
        val demoReplies = listOf(
            "Line looked smooth there.",
            "Try braking later before the section.",
            "Great speed through the lower part.",
            "Keep your body low on fast segments.",
            "Nice run, conditions seem dry today."
        )
    }
}

private data class BaseCommunityState(
    val currentUser: CommunityUser? = null,
    val riders: List<CommunityRider> = emptyList(),
    val messages: List<CommunityMessage> = emptyList()
)

private data class CommunityInputState(
    val username: String = "",
    val location: String = "",
    val message: String = "",
    val selectedRider: String? = null,
    val registrationErrorCode: String? = null
)
