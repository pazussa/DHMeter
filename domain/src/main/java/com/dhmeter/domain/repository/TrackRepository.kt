package com.dhmeter.domain.repository

import com.dhmeter.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    suspend fun getTrackById(trackId: String): Track?
    suspend fun insertTrack(track: Track)
    suspend fun updateTrack(track: Track)
    suspend fun deleteTrack(trackId: String)
}
