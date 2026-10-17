package com.example.agenticstarter.domain.repository

import com.example.agenticstarter.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
