package com.airmesh.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: DeviceStatsEntity)

    @Query("UPDATE device_stats SET messagesSent = messagesSent + 1, lastSeen = :now WHERE deviceName = :deviceName")
    suspend fun incrementSent(deviceName: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE device_stats SET messagesReceived = messagesReceived + 1, lastSeen = :now WHERE deviceName = :deviceName")
    suspend fun incrementReceived(deviceName: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE device_stats SET messagesCarried = messagesCarried + 1, lastSeen = :now WHERE deviceName = :deviceName")
    suspend fun incrementCarried(deviceName: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE device_stats SET lastSeen = :now WHERE deviceName = :deviceName")
    suspend fun updateLastSeen(deviceName: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM device_stats ORDER BY lastSeen DESC")
    fun getAllStats(): Flow<List<DeviceStatsEntity>>

    @Query("SELECT * FROM device_stats WHERE lastSeen > :since ORDER BY lastSeen DESC")
    fun getActiveDevices(since: Long): Flow<List<DeviceStatsEntity>>
}
