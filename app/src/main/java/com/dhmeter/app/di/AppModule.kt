package com.dhmeter.app.di

import com.dhmeter.app.data.AppPreferences
import com.dhmeter.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    
    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        appPreferences: AppPreferences
    ): PreferencesRepository
}
