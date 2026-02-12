package com.dhmeter.app.community

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _users = MutableStateFlow(loadUsers())
    private val _messages = MutableStateFlow(loadMessages())
    private val _currentUsername = MutableStateFlow(loadCurrentUsername())

    init {
        seedDefaultsIfNeeded()
    }

    fun observeUsers(): StateFlow<List<CommunityUser>> = _users.asStateFlow()

    fun observeMessages(): StateFlow<List<CommunityMessage>> = _messages.asStateFlow()

    fun observeCurrentUser(): Flow<CommunityUser?> {
        return combine(_users, _currentUsername) { users, username ->
            users.firstOrNull { it.username.equals(username, ignoreCase = true) }
        }
    }

    suspend fun registerUser(username: String, location: String): Result<CommunityUser> = mutex.withLock {
        val normalizedUsername = username.trim()
        val normalizedLocation = location.trim()
        if (normalizedUsername.isBlank()) {
            return Result.failure(IllegalArgumentException("USERNAME_REQUIRED"))
        }
        if (normalizedLocation.isBlank()) {
            return Result.failure(IllegalArgumentException("LOCATION_REQUIRED"))
        }

        val isTaken = _users.value.any { it.username.equals(normalizedUsername, ignoreCase = true) }
        if (isTaken) {
            return Result.failure(IllegalArgumentException("USERNAME_TAKEN"))
        }

        val user = CommunityUser(
            username = normalizedUsername,
            location = normalizedLocation,
            joinedAt = System.currentTimeMillis(),
            isLocal = true
        )

        val updatedUsers = (_users.value + user).distinctBy { it.username.lowercase() }
            .sortedBy { it.username.lowercase() }
        _users.value = updatedUsers
        _currentUsername.value = user.username
        persistUsers(updatedUsers)
        persistCurrentUsername(user.username)

        addMessageInternal(
            author = "system",
            text = "${user.username} joined from ${user.location}"
        )
        Result.success(user)
    }

    suspend fun sendMessage(message: String): Result<Unit> = mutex.withLock {
        val currentUsername = _currentUsername.value
        if (currentUsername.isNullOrBlank()) {
            return Result.failure(IllegalStateException("USER_NOT_REGISTERED"))
        }
        val normalized = message.trim()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("MESSAGE_EMPTY"))
        }

        addMessageInternal(
            author = currentUsername,
            text = normalized
        )
        Result.success(Unit)
    }

    suspend fun addDemoReply(text: String): Result<Unit> = mutex.withLock {
        val demoUser = _users.value.firstOrNull { !it.isLocal } ?: return Result.success(Unit)
        addMessageInternal(
            author = demoUser.username,
            text = text.trim()
        )
        Result.success(Unit)
    }

    private fun addMessageInternal(author: String, text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        val updatedMessages = (_messages.value + CommunityMessage(
            id = UUID.randomUUID().toString(),
            author = author,
            text = normalized,
            sentAt = System.currentTimeMillis()
        ))
            .sortedBy { it.sentAt }
            .takeLast(MAX_STORED_MESSAGES)

        _messages.value = updatedMessages
        persistMessages(updatedMessages)
    }

    private fun seedDefaultsIfNeeded() {
        if (_users.value.isNotEmpty()) return

        val seededUsers = listOf(
            CommunityUser(
                username = "RafaDH",
                location = "Medellin",
                joinedAt = System.currentTimeMillis() - 86_400_000L,
                isLocal = false
            ),
            CommunityUser(
                username = "LunaRider",
                location = "Quito",
                joinedAt = System.currentTimeMillis() - 52_000_000L,
                isLocal = false
            ),
            CommunityUser(
                username = "MatiFlow",
                location = "Santiago",
                joinedAt = System.currentTimeMillis() - 26_000_000L,
                isLocal = false
            )
        )
        _users.value = seededUsers
        persistUsers(seededUsers)

        if (_messages.value.isEmpty()) {
            val initialMessages = listOf(
                CommunityMessage(
                    id = UUID.randomUUID().toString(),
                    author = "RafaDH",
                    text = "Ready for weekend downhill sessions?",
                    sentAt = System.currentTimeMillis() - 300_000L
                ),
                CommunityMessage(
                    id = UUID.randomUUID().toString(),
                    author = "LunaRider",
                    text = "Yes, checking lines and braking points today.",
                    sentAt = System.currentTimeMillis() - 180_000L
                )
            )
            _messages.value = initialMessages
            persistMessages(initialMessages)
        }
    }

    private fun loadCurrentUsername(): String? {
        return prefs.getString(KEY_CURRENT_USERNAME, null)
    }

    private fun loadUsers(): List<CommunityUser> {
        val raw = prefs.getString(KEY_USERS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        CommunityUser(
                            username = item.optString("username"),
                            location = item.optString("location"),
                            joinedAt = item.optLong("joinedAt"),
                            isLocal = item.optBoolean("isLocal", false)
                        )
                    )
                }
            }.filter { it.username.isNotBlank() && it.location.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun loadMessages(): List<CommunityMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        CommunityMessage(
                            id = item.optString("id"),
                            author = item.optString("author"),
                            text = item.optString("text"),
                            sentAt = item.optLong("sentAt")
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.author.isNotBlank() && it.text.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun persistCurrentUsername(username: String?) {
        prefs.edit()
            .putString(KEY_CURRENT_USERNAME, username)
            .apply()
    }

    private fun persistUsers(users: List<CommunityUser>) {
        val array = JSONArray()
        users.forEach { user ->
            array.put(
                JSONObject()
                    .put("username", user.username)
                    .put("location", user.location)
                    .put("joinedAt", user.joinedAt)
                    .put("isLocal", user.isLocal)
            )
        }
        prefs.edit()
            .putString(KEY_USERS, array.toString())
            .apply()
    }

    private fun persistMessages(messages: List<CommunityMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("author", message.author)
                    .put("text", message.text)
                    .put("sentAt", message.sentAt)
            )
        }
        prefs.edit()
            .putString(KEY_MESSAGES, array.toString())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "dropin_dh_community"
        const val KEY_USERS = "community_users_json"
        const val KEY_MESSAGES = "community_messages_json"
        const val KEY_CURRENT_USERNAME = "community_current_username"
        const val MAX_STORED_MESSAGES = 300
    }
}
