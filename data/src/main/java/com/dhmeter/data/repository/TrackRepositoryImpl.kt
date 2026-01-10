package com.dhmeter.data.repository

import com.dhmeter.data.local.dao.TrackDao
import com.dhmeter.data.mapper.toDomain
import com.dhmeter.data.mapper.toEntity
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao
) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return trackDao.getAllTracks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTrackById(trackId: String): Track? {
        return trackDao.getTrackById(trackId)?.toDomain()
    }

    override suspend fun insertTrack(track: Track) {
        trackDao.insertTrack(track.toEntity())
    }

    override suspend fun updateTrack(track: Track) {
        trackDao.updateTrack(track.toEntity())
    }

    override suspend fun deleteTrack(trackId: String) {
        trackDao.deleteTrack(trackId)
    }
}
