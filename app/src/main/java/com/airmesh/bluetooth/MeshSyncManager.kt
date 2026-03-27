package com.airmesh.bluetooth

import android.util.Log
import com.airmesh.data.model.MeshMessage
import com.airmesh.data.model.NearbyDevice
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
    private val classicBtManager: ClassicBtManager,
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
    // Track which MACs we've synced recently to avoid hammering the same device
    private val recentlySynced = mutableMapOf<String, Long>()
    private val SYNC_COOLDOWN_MS = 60_000L

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        val myName = runBlocking { preferencesManager.usernameFlow.first() }
        if (myName.isBlank()) {
            Log.w(TAG, "Username not set — mesh sync deferred")
            return
        }
        setupClassicBt(myName)
        classicBtManager.startServer(coroutineScope)
        bleManager.startAdvertising(myName)
        bleManager.startScanning()

        // Observe nearby devices and trigger sync
        coroutineScope.launch {
            bleManager.nearbyDevicesFlow.collectLatest { devices ->
                devices.forEach { device ->
                    deviceRepository.updateLastSeen(device.name)
                    triggerSyncIfNeeded(device)
                }
            }
        }

        // Periodic sync sweep
        coroutineScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                bleManager.pruneStaleDevices()
                bleManager.nearbyDevicesFlow.value.forEach { device ->
                    triggerSyncIfNeeded(device, force = true)
                }
            }
        }
    }

    fun stop() {
        bleManager.stopAll()
        classicBtManager.stopServer()
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
        Log.d(TAG, "Message queued: $message")

        // Attempt immediate sync with any nearby device
        scope?.launch(Dispatchers.IO) {
            bleManager.nearbyDevicesFlow.value.forEach { device ->
                classicBtManager.connectAndSync(device.macAddress)
            }
        }
    }

    private fun setupClassicBt(myName: String) {
        classicBtManager.getMessagesToSend = {
            messageRepository.getUndeliveredMessages()
                .filter { it.ttl > 0 }
        }

        classicBtManager.onMessagesReceived = { messages, peerName ->
            deviceRepository.updateLastSeen(peerName)
            messages.forEach { msg ->
                processIncomingMessage(msg, myName, peerName)
            }
        }
    }

    private suspend fun processIncomingMessage(
        message: MeshMessage,
        myName: String,
        peerName: String
    ) {
        // Decrement TTL for relay; discard expired
        val decremented = message.copy(
            ttl = message.ttl - 1,
            hopPath = message.hopPath + myName
        )
        if (decremented.ttl <= 0) {
            Log.d(TAG, "Message ${message.messageId} TTL expired, discarding")
            return
        }

        // Save for relay (or direct delivery)
        messageRepository.saveMessage(decremented)

        if (decremented.receiverId == myName) {
            // Delivered to us
            messageRepository.markDelivered(decremented.messageId)
            deviceRepository.recordReceived(decremented.senderId)
            notificationHelper.showMessageNotification(decremented.senderId, decremented.content)
            Log.d(TAG, "Message delivered to us from ${decremented.senderId}")
        } else {
            // We are relaying
            deviceRepository.recordCarried(peerName)
            Log.d(TAG, "Relaying message ${message.messageId} to ${decremented.receiverId}")
        }
    }

    private fun triggerSyncIfNeeded(device: NearbyDevice, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastSync = recentlySynced[device.macAddress] ?: 0L
        if (!force && now - lastSync < SYNC_COOLDOWN_MS) return

        recentlySynced[device.macAddress] = now
        scope?.launch(Dispatchers.IO) {
            classicBtManager.connectAndSync(device.macAddress)
        }
    }
}
