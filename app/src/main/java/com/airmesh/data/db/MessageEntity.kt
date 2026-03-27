package com.airmesh.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val ttl: Int,
    val hopPath: String, // JSON array stored as string
    val delivered: Boolean = false
)
