package com.example.agenticstarter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agenticstarter.domain.model.ThemeMode
import com.example.agenticstarter.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
}
