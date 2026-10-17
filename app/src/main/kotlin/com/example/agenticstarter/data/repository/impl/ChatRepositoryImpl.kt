package com.example.agenticstarter.data.repository.impl

import com.example.agenticstarter.core.common.AppError
import com.example.agenticstarter.core.common.Outcome
import com.example.agenticstarter.core.common.map
import com.example.agenticstarter.core.common.outcomeOf
import com.example.agenticstarter.data.local.dao.ChatMessageDao
import com.example.agenticstarter.data.local.entity.ChatMessageEntity
import com.example.agenticstarter.domain.model.ChatMessage
import com.example.agenticstarter.domain.model.ChatRole
import com.example.agenticstarter.domain.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl @Inject constructor(private val dao: ChatMessageDao) : ChatRepository {
    override fun observeMessages(): Flow<List<ChatMessage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun append(role: ChatRole, text: String): Outcome<Unit> =
        outcomeOf(onError = { AppError.Storage(it) }) {
            dao.insert(ChatMessageEntity(role = role.name, text = text, createdAtMillis = System.currentTimeMillis()))
        }.map { }
}

private fun ChatMessageEntity.toDomain() =
    ChatMessage(id = id, role = ChatRole.valueOf(role), text = text, createdAtMillis = createdAtMillis)
