package com.example.agenticstarter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.agenticstarter.data.local.dao.ChatMessageDao
import com.example.agenticstarter.data.local.entity.ChatMessageEntity

@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
}
