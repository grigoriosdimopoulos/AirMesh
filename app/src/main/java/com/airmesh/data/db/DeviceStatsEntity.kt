package com.airmesh.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_stats")
data class DeviceStatsEntity(
    @PrimaryKey val deviceName: String,
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0,
    val messagesCarried: Int = 0,
    val lastSeen: Long = System.currentTimeMillis()
)
