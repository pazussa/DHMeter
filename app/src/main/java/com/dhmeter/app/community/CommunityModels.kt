package com.dropindh.app.community

data class CommunityUser(
    val uid: String = "",
    val username: String,
    val location: String,
    val joinedAt: Long,
    val isLocal: Boolean,
    val progress: RiderProgress = RiderProgress()
)

data class CommunityMessage(
    val id: String,
    val authorUid: String = "",
    val author: String,
    val text: String,
    val sentAt: Long
)

data class CommunityReport(
    val id: String,
    val reporter: String,
    val targetUsername: String,
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: String,
    val createdAt: Long
)

enum class ReportTargetType {
    MESSAGE,
    USER_PROFILE
}

data class RiderProgress(
    val totalRuns: Int = 0,
    val bestTimeSeconds: Double? = null,
    val avgSpeed: Double? = null,
    val maxSpeed: Double? = null
)

data class CommunityRider(
    val user: CommunityUser,
    val progress: RiderProgress
)

