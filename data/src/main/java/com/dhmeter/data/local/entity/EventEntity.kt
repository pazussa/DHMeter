package com.dhmeter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId"), Index("type")]
)
data class EventEntity(
    @PrimaryKey
    val eventId: String,
    val runId: String,
    val type: String, // LANDING, IMPACT_PEAK, HARSHNESS_BURST
    val distPct: Float,
    val timeSec: Float,
    val severity: Float,
    val meta: String? // JSON: {peakG, energy300ms, recoveryMs, etc.}
)
