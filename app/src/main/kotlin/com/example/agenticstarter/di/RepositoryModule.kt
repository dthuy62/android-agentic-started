package com.example.agenticstarter.di

import com.example.agenticstarter.data.engine.EchoChatEngine
import com.example.agenticstarter.data.preferences.DataStoreSettingsRepository
import com.example.agenticstarter.data.repository.impl.ChatRepositoryImpl
import com.example.agenticstarter.domain.engine.ChatEngine
import com.example.agenticstarter.domain.repository.ChatRepository
import com.example.agenticstarter.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    abstract fun bindChatEngine(impl: EchoChatEngine): ChatEngine

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
