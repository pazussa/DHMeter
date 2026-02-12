package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.PreferencesRepository
import com.dhmeter.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetRunsByTrackUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    operator fun invoke(trackId: String): Flow<List<Run>> {
        return runRepository.getRunsByTrack(trackId)
    }
}

class GetRunByIdUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke(runId: String): Result<Run> {
        return runRepository.getRunById(runId)?.let {
            Result.success(it)
        } ?: Result.failure(Exception("Run not found"))
    }
}

/**
 * Get comparable runs for a given track.
 * Returns runs according to the "include invalid runs" preference.
 */
class GetComparableRunsUseCase @Inject constructor(
    private val runRepository: RunRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(trackId: String, excludeRunId: String): Result<List<Run>> {
        return try {
            val includeInvalid = preferencesRepository.getIncludeInvalidRuns()

            val runs = runRepository.getRunsByTrack(trackId).first()
                .filter { it.runId != excludeRunId }
                .filter { includeInvalid || it.isValid }
                .sortedByDescending { it.startedAt }

            Result.success(runs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class GetRunCountByTrackUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke(trackId: String): Result<Int> {
        return try {
            Result.success(runRepository.getRunCountByTrack(trackId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class GetRunSeriesUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke(runId: String, seriesType: SeriesType): Result<RunSeries?> {
        return try {
            Result.success(runRepository.getSeries(runId, seriesType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class GetRunEventsUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke(runId: String): Result<List<RunEvent>> {
        return try {
            Result.success(runRepository.getEvents(runId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
