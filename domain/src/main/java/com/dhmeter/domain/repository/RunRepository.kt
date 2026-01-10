package com.dhmeter.domain.repository

import com.dhmeter.domain.model.*
import kotlinx.coroutines.flow.Flow

interface RunRepository {
    fun getRunsByTrack(trackId: String): Flow<List<Run>>
    suspend fun getRunById(runId: String): Run?
    suspend fun getValidRunsByTrack(trackId: String): List<Run>
    suspend fun getRunCountByTrack(trackId: String): Int
    suspend fun insertRun(run: Run)
    suspend fun updateRun(run: Run)
    suspend fun deleteRun(runId: String)
    
    // Series
    suspend fun insertSeries(series: RunSeries)
    suspend fun getSeries(runId: String, seriesType: SeriesType): RunSeries?
    suspend fun getAllSeries(runId: String): List<RunSeries>
    
    // Events
    suspend fun insertEvents(events: List<RunEvent>)
    suspend fun getEvents(runId: String): List<RunEvent>
    suspend fun getEventsByType(runId: String, type: String): List<RunEvent>
    
    // GPS Polyline
    suspend fun insertGpsPolyline(polyline: GpsPolyline)
    suspend fun getGpsPolyline(runId: String): GpsPolyline?
}
