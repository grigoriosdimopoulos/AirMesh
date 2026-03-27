package com.airmesh.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.airmesh.data.model.NearbyDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleManager"
        // Shared service UUID — all AirMesh devices advertise this
        val AIRMESH_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        private val SERVICE_PARCEL_UUID = ParcelUuid(AIRMESH_SERVICE_UUID)
        private const val DEVICE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevicesFlow: StateFlow<List<NearbyDevice>> = _nearbyDevices

    // Map of MAC → NearbyDevice for dedup
    private val deviceMap = mutableMapOf<String, NearbyDevice>()

    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var myName: String = ""

    fun startAdvertising(userName: String) {
        myName = userName
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        bleAdvertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // Encode username in manufacturer data (first 20 bytes)
        val nameBytes = userName.toByteArray(Charsets.UTF_8).take(20).toByteArray()
        val data = AdvertiseData.Builder()
            .addServiceUuid(SERVICE_PARCEL_UUID)
            .addManufacturerData(0xAE11, nameBytes)
            .setIncludeDeviceName(false)
            .build()

        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.d(TAG, "BLE advertising started as $userName")
    }

    fun startScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        bleScanner = adapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(SERVICE_PARCEL_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
            }
        }
        scanCallback = cb
        bleScanner?.startScan(listOf(filter), settings, cb)
        Log.d(TAG, "BLE scanning started")
    }

    fun stopAll() {
        runCatching {
            if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                scanCallback?.let { bleScanner?.stopScan(it) }
            }
            if (hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
            }
        }
        scanCallback = null
        Log.d(TAG, "BLE stopped")
    }

    fun pruneStaleDevices() {
        val cutoff = System.currentTimeMillis() - DEVICE_TIMEOUT_MS
        val stale = deviceMap.entries.filter { it.value.lastSeen < cutoff }.map { it.key }
        stale.forEach { deviceMap.remove(it) }
        if (stale.isNotEmpty()) _nearbyDevices.value = deviceMap.values.toList()
    }

    private fun handleScanResult(result: ScanResult) {
        val macAddress = result.device.address
        val rssi = result.rssi
        // Extract username from manufacturer data (manufacturer ID 0xAE11)
        val manufData = result.scanRecord?.getManufacturerSpecificData(0xAE11)
        val deviceName = if (manufData != null) {
            String(manufData, Charsets.UTF_8).trim()
        } else {
            macAddress
        }

        if (deviceName == myName) return // skip ourselves

        val device = NearbyDevice(
            name = deviceName,
            macAddress = macAddress,
            rssi = rssi,
            lastSeen = System.currentTimeMillis()
        )
        deviceMap[macAddress] = device
        _nearbyDevices.value = deviceMap.values.sortedByDescending { it.rssi }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising started successfully")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: $errorCode")
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
