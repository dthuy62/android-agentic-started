package com.example.agenticstarter.domain.usecase

import com.example.agenticstarter.core.common.AppError
import com.example.agenticstarter.core.common.DispatcherProvider
import com.example.agenticstarter.core.common.Outcome
import com.example.agenticstarter.core.common.toAppError
import com.example.agenticstarter.domain.engine.ChatEngine
import com.example.agenticstarter.domain.model.ChatRole
import com.example.agenticstarter.domain.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed interface ReplyProgress {
    /** Reply text accumulated so far. */
    data class Streaming(val text: String) : ReplyProgress

    data object Done : ReplyProgress

    data class Failed(val error: AppError) : ReplyProgress
}

/**
 * Real business logic, so it earns a use case: persist the user turn, stream the reply,
 * persist the assistant turn. A pass-through repository call would NOT get one.
 */
class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository,
    private val engine: ChatEngine,
    private val dispatchers: DispatcherProvider,
) {
    operator fun invoke(text: String): Flow<ReplyProgress> = flow {
        val saved = repository.append(ChatRole.User, text)
        if (saved is Outcome.Failure) {
            emit(ReplyProgress.Failed(saved.error))
            return@flow
        }
        val history = repository.observeMessages().first()
        val reply = StringBuilder()
        engine.streamReply(history).collect { token ->
            reply.append(token)
            emit(ReplyProgress.Streaming(reply.toString()))
        }
        when (val result = repository.append(ChatRole.Assistant, reply.toString())) {
            is Outcome.Success -> emit(ReplyProgress.Done)
            is Outcome.Failure -> emit(ReplyProgress.Failed(result.error))
        }
    }.catch { emit(ReplyProgress.Failed(it.toAppError())) }
        .flowOn(dispatchers.default)
}
