package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.Track
import com.dhmeter.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class GetTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(): Flow<List<Track>> {
        return trackRepository.getAllTracks()
    }
}

class GetTrackByIdUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(trackId: String): Result<Track> {
        return trackRepository.getTrackById(trackId)?.let {
            Result.success(it)
        } ?: Result.failure(Exception("Track not found"))
    }
}

class CreateTrackUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(name: String, locationHint: String?): Result<Track> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("Track name cannot be empty"))
        }
        
        val track = Track(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
            locationHint = locationHint?.trim()
        )
        
        return try {
            trackRepository.insertTrack(track)
            Result.success(track)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class DeleteTrackUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(trackId: String): Result<Unit> {
        if (trackId.isBlank()) {
            return Result.failure(IllegalArgumentException("Track id cannot be empty"))
        }

        return try {
            trackRepository.deleteTrack(trackId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
