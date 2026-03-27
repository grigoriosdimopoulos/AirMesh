package com.airmesh.data.model

data class MeshMessage(
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val ttl: Int = 10,
    val hopPath: List<String> = emptyList()
)
