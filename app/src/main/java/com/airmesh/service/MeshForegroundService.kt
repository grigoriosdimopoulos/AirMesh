package com.airmesh.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.airmesh.bluetooth.MeshSyncManager
import com.airmesh.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshForegroundService"
    }

    @Inject lateinit var meshSyncManager: MeshSyncManager
    @Inject lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            notificationHelper.buildServiceNotification()
        )
        meshSyncManager.start(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service start command received")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        meshSyncManager.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
