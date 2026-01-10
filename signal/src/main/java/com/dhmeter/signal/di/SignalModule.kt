package com.dhmeter.signal.di

import com.dhmeter.domain.usecase.RunProcessor
import com.dhmeter.signal.processor.GpsPolylineProcessor
import com.dhmeter.signal.processor.SignalProcessor
import com.dhmeter.signal.metrics.HarshnessAnalyzer
import com.dhmeter.signal.metrics.ImpactAnalyzer
import com.dhmeter.signal.metrics.LandingDetector
import com.dhmeter.signal.metrics.StabilityAnalyzer
import com.dhmeter.signal.validation.RunValidator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SignalModule {

    @Binds
    @Singleton
    abstract fun bindRunProcessor(impl: SignalProcessor): RunProcessor
}

@Module
@InstallIn(SingletonComponent::class)
object SignalProviderModule {

    @Provides
    @Singleton
    fun provideImpactAnalyzer(): ImpactAnalyzer = ImpactAnalyzer()

    @Provides
    @Singleton
    fun provideHarshnessAnalyzer(): HarshnessAnalyzer = HarshnessAnalyzer()

    @Provides
    @Singleton
    fun provideStabilityAnalyzer(): StabilityAnalyzer = StabilityAnalyzer()

    @Provides
    @Singleton
    fun provideLandingDetector(): LandingDetector = LandingDetector()

    @Provides
    @Singleton
    fun provideRunValidator(): RunValidator = RunValidator()
    
    @Provides
    @Singleton
    fun provideGpsPolylineProcessor(): GpsPolylineProcessor = GpsPolylineProcessor()
}
