package com.airmesh.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.airmesh.MainActivity
import com.airmesh.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_SERVICE = "airmesh_service"
        const val CHANNEL_MESSAGES = "airmesh_messages"
        const val NOTIFICATION_ID_SERVICE = 1
        private const val NOTIFICATION_ID_MESSAGE = 2
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps AirMesh mesh network active"
            setShowBadge(false)
        }

        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New message alerts"
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(messageChannel)
    }

    fun buildServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_hermes)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun showMessageNotification(senderName: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_chat", senderName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, senderName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(context.getString(R.string.notification_message_title, senderName))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_hermes)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MESSAGE + senderName.hashCode(), notification)
    }
}
