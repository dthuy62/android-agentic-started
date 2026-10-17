package com.example.agenticstarter.domain.model

enum class ChatRole { User, Assistant }

data class ChatMessage(val id: Long, val role: ChatRole, val text: String, val createdAtMillis: Long)
