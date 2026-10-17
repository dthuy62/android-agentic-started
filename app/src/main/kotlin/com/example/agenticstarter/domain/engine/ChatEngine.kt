package com.example.agenticstarter.domain.engine

import com.example.agenticstarter.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Port for whatever produces the assistant reply (remote API, Gemini, or an on-device model).
 * Emits text deltas (tokens). The use case accumulates them; the engine never touches storage or UI.
 */
interface ChatEngine {
    fun streamReply(history: List<ChatMessage>): Flow<String>
}
