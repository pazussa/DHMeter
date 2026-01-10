package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.ProcessedRun
import com.dhmeter.domain.model.RawCaptureHandle
import com.dhmeter.domain.repository.RunRepository
import javax.inject.Inject

/**
 * Processes raw sensor capture data and saves the results.
 * This is a facade that delegates to the signal processing module.
 */
class ProcessRunUseCase @Inject constructor(
    private val runRepository: RunRepository,
    private val runProcessor: RunProcessor
) {
    suspend operator fun invoke(captureHandle: RawCaptureHandle): Result<String> {
        return try {
            // Process the raw capture data
            val processedRun = runProcessor.process(captureHandle)
            
            // Save to database
            runRepository.insertRun(processedRun.run)
            processedRun.series.forEach { series ->
                runRepository.insertSeries(series)
            }
            runRepository.insertEvents(processedRun.events)
            
            // Save GPS polyline for map visualization
            processedRun.gpsPolyline?.let { polyline ->
                runRepository.insertGpsPolyline(polyline)
            }
            
            Result.success(processedRun.run.runId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Interface for the signal processing module.
 */
interface RunProcessor {
    suspend fun process(handle: RawCaptureHandle): ProcessedRun
}
