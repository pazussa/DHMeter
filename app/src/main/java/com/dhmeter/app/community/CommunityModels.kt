package com.dhmeter.app.community

data class CommunityUser(
    val username: String,
    val location: String,
    val joinedAt: Long,
    val isLocal: Boolean
)

data class CommunityMessage(
    val id: String,
    val author: String,
    val text: String,
    val sentAt: Long
)

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

