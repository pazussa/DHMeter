package com.dhmeter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val trackId: String,
    val name: String,
    val createdAt: Long,
    val locationHint: String?,
    val notes: String?
)
