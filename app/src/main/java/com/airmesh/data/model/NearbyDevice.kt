package com.airmesh.data.model

data class NearbyDevice(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
