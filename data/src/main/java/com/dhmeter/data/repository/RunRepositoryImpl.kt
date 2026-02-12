package com.dhmeter.data.repository

import com.dhmeter.data.local.dao.RunDao
import com.dhmeter.data.mapper.*
import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RunRepositoryImpl @Inject constructor(
    private val runDao: RunDao
) : RunRepository {

    override fun getRunsByTrack(trackId: String): Flow<List<Run>> {
        return runDao.getRunsByTrack(trackId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRunById(runId: String): Run? {
        return runDao.getRunById(runId)?.toDomain()
    }

    override suspend fun getRunCountByTrack(trackId: String): Int {
        return runDao.getRunCountByTrack(trackId)
    }

    override suspend fun insertRun(run: Run) {
        runDao.insertRun(run.toEntity())
    }

    override suspend fun updateRun(run: Run) {
        runDao.updateRun(run.toEntity())
    }

    override suspend fun deleteRun(runId: String) {
        runDao.deleteRun(runId)
    }

    // Series
    override suspend fun insertSeries(series: RunSeries) {
        runDao.insertSeries(series.toEntity())
    }

    override suspend fun getSeries(runId: String, seriesType: SeriesType): RunSeries? {
        return runDao.getSeries(runId, seriesType.name)?.toDomain()
    }

    override suspend fun getAllSeries(runId: String): List<RunSeries> {
        return runDao.getAllSeries(runId).map { it.toDomain() }
    }

    // Events
    override suspend fun insertEvents(events: List<RunEvent>) {
        runDao.insertEvents(events.map { it.toEntity() })
    }

    override suspend fun getEvents(runId: String): List<RunEvent> {
        return runDao.getEvents(runId).map { it.toDomain() }
    }

    override suspend fun getEventsByType(runId: String, type: String): List<RunEvent> {
        return runDao.getEventsByType(runId, type).map { it.toDomain() }
    }
    
    // GPS Polyline
    override suspend fun insertGpsPolyline(polyline: GpsPolyline) {
        runDao.insertGpsPolyline(GpsPolylineMapper.domainToEntity(polyline))
    }
    
    override suspend fun getGpsPolyline(runId: String): GpsPolyline? {
        return runDao.getGpsPolyline(runId)?.let { GpsPolylineMapper.entityToDomain(it) }
    }
}
