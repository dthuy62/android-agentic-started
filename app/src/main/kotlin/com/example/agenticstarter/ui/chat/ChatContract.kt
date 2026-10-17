package com.example.agenticstarter.ui.chat

import androidx.compose.runtime.Immutable
import com.example.agenticstarter.core.common.AppError
import com.example.agenticstarter.core.mvi.UiEffect
import com.example.agenticstarter.core.mvi.UiIntent
import com.example.agenticstarter.core.mvi.UiState
import com.example.agenticstarter.domain.model.ChatMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class ChatState(
    val messages: ImmutableList<ChatMessage> = persistentListOf(),
    val input: String = "",
    /** Non-null while the assistant reply is being streamed. */
    val streamingText: String? = null,
    val error: AppError? = null,
) : UiState {
    val isStreaming: Boolean get() = streamingText != null
    val canSend: Boolean get() = input.isNotBlank() && !isStreaming
}

sealed interface ChatIntent : UiIntent {
    data class InputChanged(val text: String) : ChatIntent

    data object SendClicked : ChatIntent

    data object ErrorDismissed : ChatIntent

    data object SettingsClicked : ChatIntent
}

sealed interface ChatEffect : UiEffect {
    data object ScrollToBottom : ChatEffect

    data object NavigateToSettings : ChatEffect
}
