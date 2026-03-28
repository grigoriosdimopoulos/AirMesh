package com.airmesh.bluetooth

import android.util.Log
import com.airmesh.data.model.MeshMessage
import com.airmesh.data.repository.DeviceRepository
import com.airmesh.data.repository.MessageRepository
import com.airmesh.notification.NotificationHelper
import com.airmesh.util.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshSyncManager @Inject constructor(
    private val bleManager: BleManager,
    private val classicBtManager: ClassicBtManager, // kept for DI compatibility, unused
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private const val TAG = "MeshSyncManager"
        private const val SYNC_INTERVAL_MS = 30_000L
        private const val INITIAL_TTL = 10
    }

    private var scope: CoroutineScope? = null

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        val myName = runBlocking { preferencesManager.usernameFlow.first() }
        if (myName.isBlank()) {
            Log.w(TAG, "Username not set — mesh sync deferred")
            return
        }

        // Wire BLE callbacks
        bleManager.getMessagesToSend = {
            messageRepository.getUndeliveredMessages().filter { it.ttl > 0 }
        }

        bleManager.onMessageReceived = { message, peerName ->
            processIncomingMessage(message, myName, peerName)
        }

        bleManager.start(coroutineScope, myName)

        // Update device last-seen timestamps from BLE observations
        coroutineScope.launch {
            bleManager.nearbyDevicesFlow.collectLatest { devices ->
                devices.forEach { device ->
                    deviceRepository.updateLastSeen(device.name)
                }
            }
        }

        // Periodic stale device pruning
        coroutineScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                bleManager.pruneStaleDevices()
            }
        }
    }

    fun stop() {
        bleManager.stop()
        scope?.cancel()
        scope = null
    }

    suspend fun sendMessage(receiverId: String, content: String) {
        val myName = preferencesManager.usernameFlow.first()
        if (myName.isBlank()) return
        val message = MeshMessage(
            messageId = UUID.randomUUID().toString(),
            senderId = myName,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis(),
            ttl = INITIAL_TTL,
            hopPath = listOf(myName)
        )
        messageRepository.saveMessage(message)
        deviceRepository.recordSent(receiverId)
        Log.d(TAG, "Message queued for delivery to $receiverId")
        // BleManager will push the message on next GATT connection to a nearby peer
    }

    private suspend fun processIncomingMessage(
        message: MeshMessage,
        myName: String,
        peerName: String
    ) {
        val decremented = message.copy(
            ttl = message.ttl - 1,
            hopPath = message.hopPath + myName
        )
        if (decremented.ttl <= 0) {
            Log.d(TAG, "Message ${message.messageId} TTL expired, discarding")
            return
        }

        messageRepository.saveMessage(decremented)

        if (decremented.receiverId == myName) {
            messageRepository.markDelivered(decremented.messageId)
            deviceRepository.recordReceived(decremented.senderId)
            notificationHelper.showMessageNotification(decremented.senderId, decremented.content)
            Log.d(TAG, "Message delivered to us from ${decremented.senderId}")
        } else {
            deviceRepository.recordCarried(peerName)
            Log.d(TAG, "Relaying message ${message.messageId} toward ${decremented.receiverId}")
        }
    }
}
