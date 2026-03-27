package com.airmesh.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.airmesh.data.model.MeshMessage
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassicBtManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "ClassicBtManager"
        private const val SERVICE_NAME = "AirMesh"
        // Same UUID as BLE service for correlation
        private val RFCOMM_UUID = BleManager.AIRMESH_SERVICE_UUID
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null

    /** Called when messages arrive from a peer during a sync session. */
    var onMessagesReceived: (suspend (List<MeshMessage>, peerName: String) -> Unit)? = null

    /** Called to get current messages to send to a peer during sync. */
    var getMessagesToSend: (suspend () -> List<MeshMessage>)? = null

    fun startServer(scope: CoroutineScope) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val adapter = bluetoothAdapter ?: return

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, RFCOMM_UUID)
                    Log.d(TAG, "Server socket waiting for connection")
                    val socket = serverSocket!!.accept()
                    serverSocket?.close()
                    launch { handleConnection(socket) }
                } catch (e: IOException) {
                    if (isActive) Log.e(TAG, "Server socket error: ${e.message}")
                    delay(3000)
                }
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    suspend fun connectAndSync(macAddress: String) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val adapter = bluetoothAdapter ?: return
        val device = runCatching { adapter.getRemoteDevice(macAddress) }.getOrNull() ?: return

        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(RFCOMM_UUID)
                adapter.cancelDiscovery()
                socket.connect()
                Log.d(TAG, "Connected to $macAddress for sync")
                handleConnection(socket)
            } catch (e: IOException) {
                Log.e(TAG, "Connect to $macAddress failed: ${e.message}")
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    private suspend fun handleConnection(socket: BluetoothSocket) {
        try {
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)

            // Protocol: send our messages first, then read peer's messages, then close
            val toSend = getMessagesToSend?.invoke() ?: emptyList()
            writeMessages(output, toSend)

            val received = readMessages(input)
            val peerName = received.firstOrNull()?.senderId ?: "unknown"
            if (received.isNotEmpty()) {
                onMessagesReceived?.invoke(received, peerName)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Connection handling error: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun writeMessages(output: DataOutputStream, messages: List<MeshMessage>) {
        val json = gson.toJson(messages)
        val bytes = json.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
        Log.d(TAG, "Sent ${messages.size} messages")
    }

    private fun readMessages(input: DataInputStream): List<MeshMessage> {
        return try {
            val length = input.readInt()
            if (length <= 0 || length > 10 * 1024 * 1024) return emptyList() // sanity 10 MB max
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val json = String(bytes, Charsets.UTF_8)
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, MeshMessage::class.java
            ).type
            gson.fromJson<List<MeshMessage>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "readMessages error: ${e.message}")
            emptyList()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
