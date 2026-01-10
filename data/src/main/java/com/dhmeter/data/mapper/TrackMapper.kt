package com.dhmeter.data.mapper

import com.dhmeter.data.local.entity.TrackEntity
import com.dhmeter.domain.model.Track

fun TrackEntity.toDomain(): Track = Track(
    id = trackId,
    name = name,
    createdAt = createdAt,
    locationHint = locationHint,
    notes = notes
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    trackId = id,
    name = name,
    createdAt = createdAt,
    locationHint = locationHint,
    notes = notes
)
