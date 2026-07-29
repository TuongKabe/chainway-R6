package com.example.koistock.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.koistock.MainActivity
import com.example.koistock.remoteLocateIntentFlow
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocateMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceRegistrar = DeviceRegistrar()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
        serviceScope.launch {
            deviceRegistrar.register(token)
                .onSuccess { Log.i("RemoteLocate", "Rotated FCM token registration succeeded") }
                .onFailure { Log.e("RemoteLocate", "Rotated FCM token registration failed", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val command = RemoteLocatePayloadParser.parse(message.data) ?: return

        remoteLocateIntentFlow.tryEmit(command)
        createNotificationChannel()
        showNotification(command)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lenh tim kho",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thong bao lenh tim san pham trong kho"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(command: RemoteLocateCommand) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("commandId", command.commandId)
            putExtra("sku", command.sku)
            putExtra("expiresAt", command.expiresAtEpochMs)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            command.commandId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Lenh tim trong kho")
            .setContentText("Tim san pham: ${command.sku}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(command.commandId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "remote_locate"
    }
}
