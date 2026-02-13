package com.dropindh.app.community

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@Singleton
class CommunityRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _users = MutableStateFlow<List<CommunityUser>>(emptyList())
    private val _messages = MutableStateFlow<List<CommunityMessage>>(emptyList())
    private val _currentUser = MutableStateFlow<CommunityUser?>(null)
    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    private val _reports = MutableStateFlow<List<CommunityReport>>(emptyList())

    private var usersListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var currentUserListener: ListenerRegistration? = null
    private var blockedUsersListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null

    init {
        if (ensureCloudReady()) {
            scope.launch {
                startRealtimeSync(force = true)
            }
        }
    }

    fun observeUsers(): StateFlow<List<CommunityUser>> = _users.asStateFlow()

    fun observeMessages(): StateFlow<List<CommunityMessage>> = _messages.asStateFlow()

    fun observeBlockedUsers(): StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    fun observeReports(): StateFlow<List<CommunityReport>> = _reports.asStateFlow()

    fun observeCurrentUser(): Flow<CommunityUser?> {
        return combine(_users, _currentUser) { users, currentUser ->
            currentUser ?: users.firstOrNull { it.isLocal }
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
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }

        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val uid = authUser.uid
        val usernameKey = normalizedUsername.lowercase()

        return try {
            val usersRef = firestore.collection(COLLECTION_USERS)
            val usernamesRef = firestore.collection(COLLECTION_USERNAMES)
            firestore.runTransaction { transaction ->
                val usernameRef = usernamesRef.document(usernameKey)
                val usernameSnapshot = transaction.get(usernameRef)
                if (usernameSnapshot.exists()) {
                    val existingUid = usernameSnapshot.getString("uid")
                    if (!existingUid.equals(uid, ignoreCase = false)) {
                        throw IllegalStateException("USERNAME_TAKEN")
                    }
                }

                val userRef = usersRef.document(uid)
                val existingUser = transaction.get(userRef)
                val previousUsernameKey = existingUser.getString("usernameKey")
                val joinedAt = existingUser.getLong("joinedAt") ?: System.currentTimeMillis()

                if (!previousUsernameKey.isNullOrBlank() && previousUsernameKey != usernameKey) {
                    transaction.delete(usernamesRef.document(previousUsernameKey))
                }

                transaction.set(
                    usernameRef,
                    mapOf(
                        "uid" to uid,
                        "username" to normalizedUsername,
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                transaction.set(
                    userRef,
                    mapOf(
                        "uid" to uid,
                        "username" to normalizedUsername,
                        "usernameKey" to usernameKey,
                        "location" to normalizedLocation,
                        "joinedAt" to joinedAt,
                        "isActive" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }.await()

            startRealtimeSync(force = true)
            val cloudUser = getUserById(uid)
                ?: CommunityUser(
                    uid = uid,
                    username = normalizedUsername,
                    location = normalizedLocation,
                    joinedAt = System.currentTimeMillis(),
                    isLocal = true
                )
            _currentUser.value = cloudUser.copy(isLocal = true)
            Result.success(cloudUser)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun sendMessage(message: String): Result<Unit> = mutex.withLock {
        val text = message.trim()
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("MESSAGE_EMPTY"))
        }
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }

        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val user = _currentUser.value ?: getUserById(authUser.uid)
            ?: return Result.failure(IllegalStateException("USER_NOT_REGISTERED"))

        return try {
            val messageRef = firestore.collection(COLLECTION_MESSAGES).document()
            messageRef.set(
                mapOf(
                    "id" to messageRef.id,
                    "authorUid" to authUser.uid,
                    "author" to user.username,
                    "text" to text,
                    "sentAt" to FieldValue.serverTimestamp(),
                    "clientSentAt" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun reportMessage(messageId: String, reason: String): Result<Unit> = mutex.withLock {
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }
        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val currentUser = _currentUser.value ?: getUserById(authUser.uid)
            ?: return Result.failure(IllegalStateException("USER_NOT_REGISTERED"))
        val message = _messages.value.firstOrNull { it.id == messageId }
            ?: return Result.failure(IllegalArgumentException("MESSAGE_NOT_FOUND"))
        val sanitizedReason = reason.trim().ifBlank { DEFAULT_REPORT_REASON }

        return try {
            val reportRef = firestore.collection(COLLECTION_REPORTS).document()
            reportRef.set(
                mapOf(
                    "id" to reportRef.id,
                    "reporterUid" to authUser.uid,
                    "reporter" to currentUser.username,
                    "targetUsername" to message.author,
                    "targetUid" to message.authorUid,
                    "targetType" to ReportTargetType.MESSAGE.name,
                    "targetId" to message.id,
                    "reason" to sanitizedReason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun reportUser(username: String, reason: String): Result<Unit> = mutex.withLock {
        val targetUsername = username.trim()
        if (targetUsername.isBlank()) {
            return Result.failure(IllegalArgumentException("REPORT_TARGET_REQUIRED"))
        }
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }
        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val currentUser = _currentUser.value ?: getUserById(authUser.uid)
            ?: return Result.failure(IllegalStateException("USER_NOT_REGISTERED"))
        val targetUser = _users.value.firstOrNull { it.username.equals(targetUsername, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("REPORT_TARGET_NOT_FOUND"))
        val sanitizedReason = reason.trim().ifBlank { DEFAULT_REPORT_REASON }

        return try {
            val reportRef = firestore.collection(COLLECTION_REPORTS).document()
            reportRef.set(
                mapOf(
                    "id" to reportRef.id,
                    "reporterUid" to authUser.uid,
                    "reporter" to currentUser.username,
                    "targetUsername" to targetUser.username,
                    "targetUid" to targetUser.uid,
                    "targetType" to ReportTargetType.USER_PROFILE.name,
                    "targetId" to targetUser.uid.ifBlank { targetUser.username.lowercase() },
                    "reason" to sanitizedReason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun blockUser(username: String): Result<Unit> = mutex.withLock {
        val targetUsername = username.trim()
        if (targetUsername.isBlank()) {
            return Result.failure(IllegalArgumentException("BLOCK_TARGET_REQUIRED"))
        }
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }
        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val currentUser = _currentUser.value ?: getUserById(authUser.uid)
            ?: return Result.failure(IllegalStateException("USER_NOT_REGISTERED"))
        if (currentUser.username.equals(targetUsername, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("BLOCK_SELF_NOT_ALLOWED"))
        }
        val target = _users.value.firstOrNull { it.username.equals(targetUsername, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("BLOCK_TARGET_NOT_FOUND"))

        return try {
            firestore.collection(COLLECTION_USERS)
                .document(authUser.uid)
                .collection(SUBCOL_BLOCKED)
                .document(target.username.lowercase())
                .set(
                    mapOf(
                        "username" to target.username,
                        "usernameKey" to target.username.lowercase(),
                        "uid" to target.uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun unblockUser(username: String): Result<Unit> = mutex.withLock {
        val usernameKey = username.trim().lowercase()
        if (usernameKey.isBlank()) {
            return Result.failure(IllegalArgumentException("BLOCK_TARGET_REQUIRED"))
        }
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }
        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))

        return try {
            firestore.collection(COLLECTION_USERS)
                .document(authUser.uid)
                .collection(SUBCOL_BLOCKED)
                .document(usernameKey)
                .delete()
                .await()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun syncCurrentUserProgress(progress: RiderProgress): Result<Unit> = mutex.withLock {
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }
        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val currentUser = _currentUser.value ?: return Result.success(Unit)
        if (currentUser.uid != authUser.uid) return Result.success(Unit)

        return try {
            firestore.collection(COLLECTION_USERS)
                .document(authUser.uid)
                .set(
                    mapOf(
                        "totalRuns" to progress.totalRuns,
                        "bestTimeSeconds" to progress.bestTimeSeconds,
                        "avgSpeed" to progress.avgSpeed,
                        "maxSpeed" to progress.maxSpeed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    suspend fun deleteCurrentAccount(): Result<Unit> = mutex.withLock {
        if (!ensureCloudReady()) {
            return Result.failure(IllegalStateException("CLOUD_NOT_CONFIGURED"))
        }
        val authUser = ensureAnonymousSession()
            ?: return Result.failure(IllegalStateException("CLOUD_AUTH_FAILED"))
        val uid = authUser.uid

        return try {
            val usersRef = firestore.collection(COLLECTION_USERS)
            val usernamesRef = firestore.collection(COLLECTION_USERNAMES)
            val userRef = usersRef.document(uid)
            val userSnapshot = userRef.get().await()
            val usernameKey = userSnapshot.getString("usernameKey").orEmpty()

            val messageDocs = firestore.collection(COLLECTION_MESSAGES)
                .whereEqualTo("authorUid", uid)
                .get()
                .await()

            val blockedDocs = userRef.collection(SUBCOL_BLOCKED)
                .get()
                .await()

            val reportsDocs = firestore.collection(COLLECTION_REPORTS)
                .whereEqualTo("reporterUid", uid)
                .get()
                .await()

            firestore.runBatch { batch ->
                if (usernameKey.isNotBlank()) {
                    batch.delete(usernamesRef.document(usernameKey))
                }

                batch.set(
                    userRef,
                    mapOf(
                        "isActive" to false,
                        "username" to "",
                        "usernameKey" to "",
                        "location" to "",
                        "deactivatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                messageDocs.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                blockedDocs.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                reportsDocs.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
            }.await()

            auth.signOut()
            startRealtimeSync(force = true)
            _currentUser.value = null
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(mapCloudError(error))
        }
    }

    private suspend fun startRealtimeSync(force: Boolean) {
        if (!ensureCloudReady()) return
        val authUser = ensureAnonymousSession() ?: return
        if (force) detachRealtimeListeners()

        attachUsersListener()
        attachMessagesListener()
        attachCurrentUserListener(authUser.uid)
        attachBlockedUsersListener(authUser.uid)
        attachReportsListener(authUser.uid)
    }

    private fun attachUsersListener() {
        if (usersListener != null) return
        usersListener = firestore.collection(COLLECTION_USERS)
            .whereEqualTo("isActive", true)
            .orderBy("username", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val currentUid = auth.currentUser?.uid
                val users = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.toCommunityUser(currentUid) }
                _users.value = users
            }
    }

    private fun attachMessagesListener() {
        if (messagesListener != null) return
        messagesListener = firestore.collection(COLLECTION_MESSAGES)
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(MAX_STORED_MESSAGES.toLong())
            .addSnapshotListener { snapshot, _ ->
                val messages = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.toCommunityMessage() }
                    .sortedBy { it.sentAt }
                _messages.value = messages
            }
    }

    private fun attachCurrentUserListener(uid: String) {
        currentUserListener?.remove()
        currentUserListener = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .addSnapshotListener { snapshot, _ ->
                _currentUser.value = snapshot?.toCommunityUser(uid)
            }
    }

    private fun attachBlockedUsersListener(uid: String) {
        blockedUsersListener?.remove()
        blockedUsersListener = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(SUBCOL_BLOCKED)
            .addSnapshotListener { snapshot, _ ->
                val blocked = snapshot?.documents.orEmpty()
                    .mapNotNull { doc ->
                        val explicit = doc.getString("usernameKey").orEmpty().trim().lowercase()
                        if (explicit.isNotBlank()) explicit else doc.id.trim().lowercase()
                    }
                    .filter { it.isNotBlank() }
                    .toSet()
                _blockedUsers.value = blocked
            }
    }

    private fun attachReportsListener(uid: String) {
        reportsListener?.remove()
        reportsListener = firestore.collection(COLLECTION_REPORTS)
            .whereEqualTo("reporterUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_STORED_REPORTS.toLong())
            .addSnapshotListener { snapshot, _ ->
                val reports = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.toCommunityReport() }
                _reports.value = reports
            }
    }

    private suspend fun ensureAnonymousSession() =
        runCatching {
            val current = auth.currentUser
            if (current != null) {
                current
            } else {
                auth.signInAnonymously().await().user
            }
        }.getOrNull()

    private suspend fun getUserById(uid: String): CommunityUser? {
        if (uid.isBlank()) return null
        val snapshot = firestore.collection(COLLECTION_USERS)
            .document(uid)
            .get()
            .await()
        return snapshot.toCommunityUser(auth.currentUser?.uid)
    }

    private fun detachRealtimeListeners() {
        usersListener?.remove()
        messagesListener?.remove()
        currentUserListener?.remove()
        blockedUsersListener?.remove()
        reportsListener?.remove()

        usersListener = null
        messagesListener = null
        currentUserListener = null
        blockedUsersListener = null
        reportsListener = null
    }

    private fun ensureCloudReady(): Boolean {
        if (!FirebaseBootstrap.isConfigured()) return false
        return FirebaseBootstrap.initialize(appContext)
    }

    private fun mapCloudError(error: Throwable): Throwable {
        return if (error is IllegalStateException || error is IllegalArgumentException) {
            error
        } else {
            IllegalStateException(error.message ?: "CLOUD_UNAVAILABLE")
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toCommunityUser(
        currentUid: String?
    ): CommunityUser? {
        val uid = getString("uid").orEmpty().ifBlank { id }
        val username = getString("username").orEmpty().trim()
        val location = getString("location").orEmpty().trim()
        val isActive = getBoolean("isActive") ?: true
        if (!isActive || uid.isBlank() || username.isBlank() || location.isBlank()) return null

        val joinedAt = getTimestamp("joinedAt")?.toDate()?.time
            ?: getLong("joinedAt")
            ?: System.currentTimeMillis()

        val totalRuns = (getLong("totalRuns") ?: 0L).toInt()
        val bestTimeSeconds = getDouble("bestTimeSeconds")
        val avgSpeed = getDouble("avgSpeed")
        val maxSpeed = getDouble("maxSpeed")

        return CommunityUser(
            uid = uid,
            username = username,
            location = location,
            joinedAt = joinedAt,
            isLocal = uid == currentUid,
            progress = RiderProgress(
                totalRuns = totalRuns,
                bestTimeSeconds = bestTimeSeconds,
                avgSpeed = avgSpeed,
                maxSpeed = maxSpeed
            )
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toCommunityMessage(): CommunityMessage? {
        val id = getString("id").orEmpty().ifBlank { this.id }
        val author = getString("author").orEmpty().trim()
        val text = getString("text").orEmpty().trim()
        if (id.isBlank() || author.isBlank() || text.isBlank()) return null

        val sentAt = getTimestamp("sentAt")?.toDate()?.time
            ?: getLong("sentAt")
            ?: getLong("clientSentAt")
            ?: System.currentTimeMillis()

        return CommunityMessage(
            id = id,
            authorUid = getString("authorUid").orEmpty(),
            author = author,
            text = text,
            sentAt = sentAt
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toCommunityReport(): CommunityReport? {
        val id = getString("id").orEmpty().ifBlank { this.id }
        val reporter = getString("reporter").orEmpty().trim()
        val targetUsername = getString("targetUsername").orEmpty().trim()
        val targetType = runCatching {
            ReportTargetType.valueOf(getString("targetType").orEmpty())
        }.getOrDefault(ReportTargetType.MESSAGE)
        val targetId = getString("targetId").orEmpty()
        val reason = getString("reason").orEmpty()
        if (id.isBlank() || reporter.isBlank() || targetUsername.isBlank() || targetId.isBlank()) {
            return null
        }

        val createdAt = getTimestamp("createdAt")?.toDate()?.time
            ?: getLong("createdAt")
            ?: System.currentTimeMillis()

        return CommunityReport(
            id = id,
            reporter = reporter,
            targetUsername = targetUsername,
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            createdAt = createdAt
        )
    }

    private companion object {
        const val COLLECTION_USERS = "community_users"
        const val COLLECTION_MESSAGES = "community_messages"
        const val COLLECTION_USERNAMES = "community_usernames"
        const val COLLECTION_REPORTS = "community_reports"
        const val SUBCOL_BLOCKED = "blocked_users"

        const val MAX_STORED_MESSAGES = 300
        const val MAX_STORED_REPORTS = 500
        const val DEFAULT_REPORT_REASON = "community_policy_violation"
    }
}

