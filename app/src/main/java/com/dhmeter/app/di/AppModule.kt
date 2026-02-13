package com.dropindh.app.di

import com.dropindh.app.monetization.LocalPurchaseValidator
import com.dropindh.app.monetization.PurchaseValidator
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
    abstract fun bindPurchaseValidator(
        impl: LocalPurchaseValidator
    ): PurchaseValidator
}

