package com.example.agenticstarter.domain.usecase

import com.example.agenticstarter.domain.model.ChatMessage
import com.example.agenticstarter.domain.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveMessagesUseCase @Inject constructor(private val repository: ChatRepository) {
    operator fun invoke(): Flow<List<ChatMessage>> = repository.observeMessages()
}
