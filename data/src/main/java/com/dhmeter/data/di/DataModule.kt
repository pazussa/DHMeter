package com.dhmeter.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dhmeter.data.local.DHMeterDatabase
import com.dhmeter.data.local.dao.RunDao
import com.dhmeter.data.local.dao.TrackDao
import com.dhmeter.data.preferences.AppPreferencesDataSource
import com.dhmeter.data.repository.RunRepositoryImpl
import com.dhmeter.data.repository.TrackRepositoryImpl
import com.dhmeter.domain.repository.PreferencesRepository
import com.dhmeter.domain.repository.RunRepository
import com.dhmeter.domain.repository.TrackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS gps_polylines (
                    runId TEXT NOT NULL PRIMARY KEY,
                    points BLOB NOT NULL,
                    pointCount INTEGER NOT NULL,
                    totalDistanceM REAL NOT NULL,
                    avgAccuracyM REAL NOT NULL,
                    gpsQuality TEXT NOT NULL,
                    FOREIGN KEY (runId) REFERENCES runs(runId) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_gps_polylines_runId ON gps_polylines(runId)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add distanceMeters and pauseCount columns to runs table
            db.execSQL("ALTER TABLE runs ADD COLUMN distanceMeters REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE runs ADD COLUMN pauseCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DHMeterDatabase {
        return Room.databaseBuilder(
            context,
            DHMeterDatabase::class.java,
            "dhmeter.db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideTrackDao(database: DHMeterDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideRunDao(database: DHMeterDatabase): RunDao = database.runDao()

    @Provides
    @Singleton
    fun provideTrackRepository(trackDao: TrackDao): TrackRepository {
        return TrackRepositoryImpl(trackDao)
    }

    @Provides
    @Singleton
    fun provideRunRepository(runDao: RunDao): RunRepository {
        return RunRepositoryImpl(runDao)
    }

    @Provides
    @Singleton
    fun providePreferencesRepository(
        appPreferencesDataSource: AppPreferencesDataSource
    ): PreferencesRepository = appPreferencesDataSource
}
