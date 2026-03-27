package com.airmesh.data.repository

import com.airmesh.data.db.DeviceStatsDao
import com.airmesh.data.db.DeviceStatsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceStatsDao: DeviceStatsDao
) {
    suspend fun ensureDevice(deviceName: String) {
        deviceStatsDao.insertIfAbsent(DeviceStatsEntity(deviceName))
    }

    suspend fun recordSent(deviceName: String) {
        ensureDevice(deviceName)
        deviceStatsDao.incrementSent(deviceName)
    }

    suspend fun recordReceived(deviceName: String) {
        ensureDevice(deviceName)
        deviceStatsDao.incrementReceived(deviceName)
    }

    suspend fun recordCarried(deviceName: String) {
        ensureDevice(deviceName)
        deviceStatsDao.incrementCarried(deviceName)
    }

    suspend fun updateLastSeen(deviceName: String) {
        ensureDevice(deviceName)
        deviceStatsDao.updateLastSeen(deviceName)
    }

    fun getAllStats(): Flow<List<DeviceStatsEntity>> = deviceStatsDao.getAllStats()

    // Active = seen in the last 5 minutes
    fun getActiveDevices(): Flow<List<DeviceStatsEntity>> =
        deviceStatsDao.getActiveDevices(System.currentTimeMillis() - 5 * 60 * 1000L)
}
