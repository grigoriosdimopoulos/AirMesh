package com.airmesh

import android.app.Application
import android.content.Intent
import com.airmesh.service.MeshForegroundService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AirMeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start mesh service on app launch
        val intent = Intent(this, MeshForegroundService::class.java)
        startForegroundService(intent)
    }
}
