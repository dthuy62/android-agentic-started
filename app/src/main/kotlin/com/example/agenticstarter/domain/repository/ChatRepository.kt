package com.example.agenticstarter.domain.repository

import com.example.agenticstarter.core.common.Outcome
import com.example.agenticstarter.domain.model.ChatMessage
import com.example.agenticstarter.domain.model.ChatRole
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(): Flow<List<ChatMessage>>

    suspend fun append(role: ChatRole, text: String): Outcome<Unit>
}
