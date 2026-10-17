package com.example.agenticstarter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agenticstarter.domain.model.ThemeMode
import com.example.agenticstarter.navigation.AppNavigation
import com.example.agenticstarter.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark =
                when (themeMode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            AppTheme(darkTheme = dark) { AppNavigation() }
        }
    }
}
