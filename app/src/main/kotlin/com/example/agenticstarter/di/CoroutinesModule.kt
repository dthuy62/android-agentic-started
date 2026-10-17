package com.example.agenticstarter.di

import com.example.agenticstarter.core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Singleton
    fun provideDispatchers(): DispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
    }
}
