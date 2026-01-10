package com.dhmeter.data.local.dao

import androidx.room.*
import com.dhmeter.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    
    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>
    
    @Query("SELECT * FROM tracks WHERE trackId = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)
    
    @Update
    suspend fun updateTrack(track: TrackEntity)
    
    @Query("DELETE FROM tracks WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: String)
}
