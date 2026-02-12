package com.dhmeter.signal.di

import com.dhmeter.domain.usecase.RunProcessor
import com.dhmeter.signal.processor.SignalProcessor
import dagger.Binds
import dagger.Module
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
