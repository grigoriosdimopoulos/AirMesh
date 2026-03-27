package com.airmesh.data.repository

import com.airmesh.data.db.ConversationSummary
import com.airmesh.data.db.MessageDao
import com.airmesh.data.db.MessageEntity
import com.airmesh.data.model.MeshMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val gson: Gson
) {
    fun getConversation(me: String, peer: String): Flow<List<MessageEntity>> =
        messageDao.getConversation(me, peer)

    fun getConversationPeers(me: String): Flow<List<ConversationSummary>> =
        messageDao.getConversationPeers(me)

    suspend fun saveMessage(message: MeshMessage) {
        messageDao.insert(message.toEntity())
    }

    suspend fun getUndeliveredMessages(): List<MeshMessage> =
        messageDao.getUndeliveredMessages().map { it.toMeshMessage(gson) }

    suspend fun markDelivered(messageId: String) = messageDao.markDelivered(messageId)

    suspend fun decrementTtl(messageId: String) = messageDao.decrementTtl(messageId)

    fun getCarriedCount(myName: String): Flow<Int> = messageDao.getCarriedCount(myName)

    fun getSentCount(myName: String): Flow<Int> = messageDao.getSentCount(myName)

    fun getReceivedCount(myName: String): Flow<Int> = messageDao.getReceivedCount(myName)

    suspend fun getLatestIncomingUndelivered(myName: String): MeshMessage? =
        messageDao.getLatestIncomingUndelivered(myName)?.toMeshMessage(gson)

    private fun MeshMessage.toEntity(): MessageEntity = MessageEntity(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        timestamp = timestamp,
        ttl = ttl,
        hopPath = gson.toJson(hopPath),
        delivered = false
    )
}

fun MessageEntity.toMeshMessage(gson: Gson): MeshMessage {
    val hopType = object : TypeToken<List<String>>() {}.type
    return MeshMessage(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        timestamp = timestamp,
        ttl = ttl,
        hopPath = gson.fromJson(hopPath, hopType) ?: emptyList()
    )
}
