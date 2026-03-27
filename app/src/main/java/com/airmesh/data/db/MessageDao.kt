package com.airmesh.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Query("""
        SELECT * FROM messages
        WHERE (senderId = :me AND receiverId = :peer) OR (senderId = :peer AND receiverId = :me)
        ORDER BY timestamp ASC
    """)
    fun getConversation(me: String, peer: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT DISTINCT CASE WHEN senderId = :me THEN receiverId ELSE senderId END AS peer,
               MAX(timestamp) as lastTs
        FROM messages
        WHERE senderId = :me OR receiverId = :me
        GROUP BY peer
        ORDER BY lastTs DESC
    """)
    fun getConversationPeers(me: String): Flow<List<ConversationSummary>>

    @Query("SELECT * FROM messages WHERE delivered = 0 AND ttl > 0")
    suspend fun getUndeliveredMessages(): List<MessageEntity>

    @Query("UPDATE messages SET delivered = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)

    @Query("UPDATE messages SET ttl = ttl - 1 WHERE messageId = :messageId")
    suspend fun decrementTtl(messageId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE receiverId != :myName AND senderId != :myName")
    fun getCarriedCount(myName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE senderId = :myName")
    fun getSentCount(myName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE receiverId = :myName")
    fun getReceivedCount(myName: String): Flow<Int>

    @Query("""
        SELECT * FROM messages
        WHERE receiverId = :myName AND delivered = 0
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestIncomingUndelivered(myName: String): MessageEntity?
}

data class ConversationSummary(
    val peer: String,
    val lastTs: Long
)
