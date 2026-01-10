package com.dhmeter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhmeter.data.local.dao.RunDao
import com.dhmeter.data.local.dao.TrackDao
import com.dhmeter.data.local.entity.*

@Database(
    entities = [
        TrackEntity::class,
        RunEntity::class,
        RunSeriesEntity::class,
        EventEntity::class,
        GpsPolylineEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class DHMeterDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun runDao(): RunDao
}
