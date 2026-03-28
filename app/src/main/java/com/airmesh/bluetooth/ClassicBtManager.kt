package com.airmesh.bluetooth

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classic Bluetooth RFCOMM is no longer used — BLE GATT handles all data transfer.
 * This stub exists only to satisfy any remaining Hilt injection sites.
 */
@Singleton
class ClassicBtManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
)
