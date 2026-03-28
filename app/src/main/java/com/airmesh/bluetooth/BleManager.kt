package com.airmesh.bluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.airmesh.data.model.MeshMessage
import com.airmesh.data.model.NearbyDevice
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified BLE manager — handles advertising, scanning, GATT server (receive) and
 * GATT client (send). No Classic Bluetooth / RFCOMM / pairing required.
 *
 * Protocol:
 *   - Each device advertises SERVICE_UUID + username in manufacturer data (connectable=true)
 *   - Each device runs a GATT server with a writable MESSAGE_CHAR
 *   - On discovering a peer, we connect as GATT client, negotiate MTU 512, then
 *     write each pending MeshMessage (JSON-encoded) to the peer's MESSAGE_CHAR
 *   - The peer's GATT server receives the write and calls onMessageReceived
 */
@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "BleManager"
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val MESSAGE_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-aabbccddeeff")
        private const val MANUFACTURER_ID = 0xAE11
        private const val SYNC_COOLDOWN_MS = 45_000L
        private const val DEVICE_TIMEOUT_MS = 5 * 60 * 1000L
        private const val GATT_TIMEOUT_MS = 20_000L
        private const val WRITE_DELAY_MS = 60L
    }

    // ── Public state ────────────────────────────────────────────────────────

    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevicesFlow: StateFlow<List<NearbyDevice>> = _nearbyDevices

    /** Provide messages to push when connecting to a peer. */
    var getMessagesToSend: (suspend () -> List<MeshMessage>)? = null

    /** Called when a message is received from a peer. */
    var onMessageReceived: (suspend (MeshMessage, peerName: String) -> Unit)? = null

    // ── Internals ────────────────────────────────────────────────────────────

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter get() = bluetoothManager?.adapter

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var gattServer: BluetoothGattServer? = null

    private val deviceMap = ConcurrentHashMap<String, NearbyDevice>()
    private val recentlySynced = ConcurrentHashMap<String, Long>()

    private var myUsername = ""
    private var scope: CoroutineScope? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start(coroutineScope: CoroutineScope, username: String) {
        scope = coroutineScope
        myUsername = username
        startGattServer()
        startAdvertising(username)
        startScanning()
        Log.i(TAG, "BLE mesh started as '$username'")
    }

    fun stop() {
        stopScanning()
        stopAdvertising()
        stopGattServer()
        scope = null
        Log.i(TAG, "BLE mesh stopped")
    }

    fun pruneStaleDevices() {
        val cutoff = System.currentTimeMillis() - DEVICE_TIMEOUT_MS
        deviceMap.entries.removeIf { it.value.lastSeen < cutoff }
        _nearbyDevices.value = deviceMap.values.toList()
    }

    // ── GATT Server (receive messages) ────────────────────────────────────────

    private fun startGattServer() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val messageChar = BluetoothGattCharacteristic(
            MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageChar)
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)?.also {
            it.addService(service)
            Log.d(TAG, "GATT server started")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            if (characteristic.uuid != MESSAGE_CHAR_UUID || value.isEmpty()) return
            runCatching {
                val json = String(value, Charsets.UTF_8)
                val message = gson.fromJson(json, MeshMessage::class.java) ?: return
                val peerName = message.senderId.ifBlank { device.address }
                scope?.launch { onMessageReceived?.invoke(message, peerName) }
            }.onFailure { Log.e(TAG, "Failed to parse incoming message: ${it.message}") }
        }
    }

    private fun stopGattServer() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        runCatching { gattServer?.close() }
        gattServer = null
    }

    // ── BLE Advertising (connectable) ─────────────────────────────────────────

    private fun startAdvertising(username: String) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "BLE advertising not supported"); return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val nameBytes = username.toByteArray(Charsets.UTF_8).take(18).toByteArray()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, nameBytes)
            .setIncludeDeviceName(false)
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(s: AdvertiseSettings) = Log.d(TAG, "Advertising OK")
            override fun onStartFailure(e: Int) = Log.e(TAG, "Advertising failed: $e")
        }
        advertiseCallback = cb
        advertiser.startAdvertising(settings, data, cb)
    }

    private fun stopAdvertising() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) return
        advertiseCallback?.let {
            runCatching { bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
        }
        advertiseCallback = null
    }

    // ── BLE Scanning ──────────────────────────────────────────────────────────

    private fun startScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner not available"); return
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) = handleScanResult(result)
            override fun onBatchScanResults(results: List<ScanResult>) =
                results.forEach { handleScanResult(it) }
            override fun onScanFailed(errorCode: Int) = Log.e(TAG, "Scan failed: $errorCode")
        }
        scanCallback = cb
        scanner.startScan(listOf(filter), settings, cb)
        Log.d(TAG, "BLE scanning started")
    }

    private fun stopScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        scanCallback?.let { runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) } }
        scanCallback = null
    }

    private fun handleScanResult(result: ScanResult) {
        val address = result.device.address
        val nameBytes = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
        val peerName = String(nameBytes, Charsets.UTF_8).trim()
        if (peerName.isBlank() || peerName == myUsername) return

        val device = NearbyDevice(peerName, address, result.rssi)
        deviceMap[address] = device
        _nearbyDevices.value = deviceMap.values.sortedByDescending { it.rssi }

        val now = System.currentTimeMillis()
        if ((now - (recentlySynced[address] ?: 0L)) > SYNC_COOLDOWN_MS) {
            recentlySynced[address] = now
            scope?.launch(Dispatchers.IO) { connectAndPush(result.device, peerName) }
        }
    }

    // ── GATT Client (push messages) ───────────────────────────────────────────

    private suspend fun connectAndPush(device: BluetoothDevice, peerName: String) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val messages = getMessagesToSend?.invoke()?.filter {
            val json = gson.toJson(it)
            json.toByteArray(Charsets.UTF_8).size <= 509 // safe MTU guard
        } ?: return
        if (messages.isEmpty()) return

        Log.d(TAG, "Connecting to $peerName ($device.address) to push ${messages.size} messages")

        val connected = CompletableDeferred<Boolean>()
        val mtuReady = CompletableDeferred<Int>()
        val servicesReady = CompletableDeferred<BluetoothGatt?>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connected.complete(true)
                        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) g.requestMtu(512)
                    }
                    else -> {
                        if (!connected.isCompleted) connected.complete(false)
                        if (!mtuReady.isCompleted) mtuReady.complete(20)
                        if (!servicesReady.isCompleted) servicesReady.complete(null)
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                mtuReady.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu - 3 else 20)
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                servicesReady.complete(if (status == BluetoothGatt.GATT_SUCCESS) g else null)
            }
        }

        var gatt: BluetoothGatt? = null
        try {
            gatt = withTimeout(GATT_TIMEOUT_MS) {
                device.connectGatt(
                    context, false, callback,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        BluetoothDevice.TRANSPORT_LE else 0
                )
                if (!connected.await()) return@withTimeout null
                servicesReady.await()
            } ?: return

            val messageChar = gatt.getService(SERVICE_UUID)
                ?.getCharacteristic(MESSAGE_CHAR_UUID) ?: return

            messages.forEach { msg ->
                val bytes = gson.toJson(msg).toByteArray(Charsets.UTF_8)
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(
                            messageChar, bytes,
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        messageChar.value = bytes
                        messageChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(messageChar)
                    }
                    delay(WRITE_DELAY_MS)
                }
            }
            delay(200) // let last write flush
            Log.d(TAG, "Pushed ${messages.size} messages to $peerName")
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "GATT connection to $peerName timed out")
        } catch (e: Exception) {
            Log.e(TAG, "GATT error with $peerName: ${e.message}")
        } finally {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                runCatching { gatt?.disconnect() }
                runCatching { gatt?.close() }
            }
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
