package com.dhmeter.data.local.dao

import androidx.room.*
import com.dhmeter.data.local.entity.EventEntity
import com.dhmeter.data.local.entity.GpsPolylineEntity
import com.dhmeter.data.local.entity.RunEntity
import com.dhmeter.data.local.entity.RunSeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    
    // Run operations
    @Query("SELECT * FROM runs WHERE trackId = :trackId ORDER BY startedAt DESC")
    fun getRunsByTrack(trackId: String): Flow<List<RunEntity>>
    
    @Query("SELECT * FROM runs WHERE runId = :runId")
    suspend fun getRunById(runId: String): RunEntity?
    
    @Query("SELECT COUNT(*) FROM runs WHERE trackId = :trackId")
    suspend fun getRunCountByTrack(trackId: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity)
    
    @Update
    suspend fun updateRun(run: RunEntity)
    
    @Query("DELETE FROM runs WHERE runId = :runId")
    suspend fun deleteRun(runId: String)
    
    // Series operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: RunSeriesEntity)
    
    @Query("SELECT * FROM run_series WHERE runId = :runId AND seriesType = :seriesType")
    suspend fun getSeries(runId: String, seriesType: String): RunSeriesEntity?
    
    @Query("SELECT * FROM run_series WHERE runId = :runId")
    suspend fun getAllSeries(runId: String): List<RunSeriesEntity>
    
    // Event operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)
    
    @Query("SELECT * FROM events WHERE runId = :runId ORDER BY distPct ASC")
    suspend fun getEvents(runId: String): List<EventEntity>
    
    @Query("SELECT * FROM events WHERE runId = :runId AND type = :type ORDER BY distPct ASC")
    suspend fun getEventsByType(runId: String, type: String): List<EventEntity>
    
    // GPS Polyline operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGpsPolyline(polyline: GpsPolylineEntity)
    
    @Query("SELECT * FROM gps_polylines WHERE runId = :runId")
    suspend fun getGpsPolyline(runId: String): GpsPolylineEntity?
}
