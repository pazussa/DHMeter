package com.dhmeter.sensing.di

import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingManagerImpl
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.sensing.preview.RecordingPreviewManager
import com.dhmeter.sensing.preview.RecordingPreviewManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensingModule {

    @Binds
    @Singleton
    abstract fun bindRecordingManager(impl: RecordingManagerImpl): RecordingManager

    @Binds
    @Singleton
    abstract fun bindRecordingPreviewManager(
        impl: RecordingPreviewManagerImpl
    ): RecordingPreviewManager
}

@Module
@InstallIn(SingletonComponent::class)
object SensingProviderModule {
    
    @Provides
    @Singleton
    fun provideSensorBuffers(): SensorBuffers = SensorBuffers(
        // Larger capacities prevent truncation on high-rate devices in longer runs.
        accelCapacity = 240_000,
        gyroCapacity = 240_000,
        rotationCapacity = 120_000,
        gpsCapacity = 3_600
    )
}
