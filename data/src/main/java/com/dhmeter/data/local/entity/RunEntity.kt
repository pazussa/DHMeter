package com.dhmeter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "runs",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId")]
)
data class RunEntity(
    @PrimaryKey
    val runId: String,
    val trackId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val isValid: Boolean,
    val invalidReason: String?,
    val phonePlacement: String,
    val deviceModel: String,
    val sampleRateAccelHz: Float,
    val sampleRateGyroHz: Float,
    val sampleRateBaroHz: Float?,
    val gpsQuality: String,
    
    // Distance and movement stats
    val distanceMeters: Float? = null,
    val pauseCount: Int = 0,
    
    // Summary metrics
    val impactScore: Float?,
    val harshnessAvg: Float?,
    val harshnessP90: Float?,
    val stabilityScore: Float?,
    val landingQualityScore: Float?,
    val avgSpeed: Float?,
    val slopeClassAvg: Int?,
    
    // User tags
    val setupNote: String?,
    val conditionsNote: String?
)
