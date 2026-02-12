package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.SegmentPoint
import com.dhmeter.domain.model.TrackSegment
import com.dhmeter.domain.repository.RunRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Builds local segment definitions from previously recorded runs.
 */
class GetTrackSegmentsUseCase @Inject constructor(
    private val runRepository: RunRepository
) {
    suspend operator fun invoke(trackId: String): Result<List<TrackSegment>> {
        return try {
            val runs = runRepository.getRunsByTrack(trackId).first()

            val segments = runs
                .mapNotNull { run ->
                    val polyline = runRepository.getGpsPolyline(run.runId) ?: return@mapNotNull null
                    val start = polyline.points.firstOrNull() ?: return@mapNotNull null
                    val end = polyline.points.lastOrNull() ?: return@mapNotNull null

                    TrackSegment(
                        id = run.runId,
                        trackId = run.trackId,
                        sourceRunId = run.runId,
                        startedAt = run.startedAt,
                        start = SegmentPoint(start.lat, start.lon),
                        end = SegmentPoint(end.lat, end.lon)
                    )
                }
                .sortedByDescending { it.startedAt }

            Result.success(segments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
