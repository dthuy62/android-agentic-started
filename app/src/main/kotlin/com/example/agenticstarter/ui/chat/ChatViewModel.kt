package com.example.agenticstarter.ui.chat

import androidx.lifecycle.viewModelScope
import com.example.agenticstarter.core.mvi.MviViewModel
import com.example.agenticstarter.domain.usecase.ObserveMessagesUseCase
import com.example.agenticstarter.domain.usecase.ReplyProgress
import com.example.agenticstarter.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    observeMessages: ObserveMessagesUseCase,
    private val sendMessage: SendMessageUseCase,
) : MviViewModel<ChatState, ChatIntent, ChatEffect>(ChatState()) {
    private var sendJob: Job? = null

    init {
        observeMessages()
            .onEach { messages ->
                setState { copy(messages = messages.toImmutableList()) }
                sendEffect(ChatEffect.ScrollToBottom)
            }.launchIn(viewModelScope)
    }

    override fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged -> setState { copy(input = intent.text) }
            ChatIntent.SendClicked -> send()
            ChatIntent.ErrorDismissed -> setState { copy(error = null) }
            ChatIntent.SettingsClicked -> sendEffect(ChatEffect.NavigateToSettings)
        }
    }

    private fun send() {
        val text = state.value.input.trim()
        if (text.isEmpty() || state.value.isStreaming) return
        setState { copy(input = "", streamingText = "", error = null) }
        sendJob?.cancel()
        sendJob =
            viewModelScope.launch {
                sendMessage(text).collect { progress ->
                    when (progress) {
                        is ReplyProgress.Streaming -> setState { copy(streamingText = progress.text) }
                        ReplyProgress.Done -> setState { copy(streamingText = null) }
                        is ReplyProgress.Failed -> setState { copy(streamingText = null, error = progress.error) }
                    }
                }
            }
    }
}
