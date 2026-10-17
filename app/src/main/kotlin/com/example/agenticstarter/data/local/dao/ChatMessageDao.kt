package com.example.agenticstarter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.agenticstarter.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
