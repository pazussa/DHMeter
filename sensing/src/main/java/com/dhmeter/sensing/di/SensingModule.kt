package com.dhmeter.sensing.di

import com.dhmeter.sensing.RecordingManager
import com.dhmeter.sensing.RecordingManagerImpl
import com.dhmeter.sensing.data.SensorBuffers
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
}

@Module
@InstallIn(SingletonComponent::class)
object SensingProviderModule {
    
    @Provides
    @Singleton
    fun provideSensorBuffers(): SensorBuffers = SensorBuffers()
}
