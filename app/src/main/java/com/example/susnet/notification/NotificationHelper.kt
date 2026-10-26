package com.example.susnet.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.susnet.MainActivity
import com.example.susnet.R
import com.example.susnet.StatusLevel

object NotificationHelper {

    const val CHANNEL_ID = "susnet_alerts"
    const val DEDUP_GAP_MS = 10 * 60 * 1000L

    private val lastNotificationTimes = mutableMapOf<String, Long>()

    fun shouldNotify(
        lastNotifyTimeMap: Map<String, Long>,
        key: String,
        currentTime: Long,
        gapMs: Long = DEDUP_GAP_MS
    ): Boolean {
        val lastTime = lastNotifyTimeMap[key] ?: 0L
        return (currentTime - lastTime) >= gapMs
    }

    @Synchronized
    fun notify(context: Context, ssid: String, level: StatusLevel) {
        if (level == StatusLevel.AMAN) return

        val key = "$ssid:$level"
        val now = System.currentTimeMillis()
        if (!shouldNotify(lastNotificationTimes, key, now)) return

        lastNotificationTimes[key] = now
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (level) {
            StatusLevel.WASPADA -> "Waspada: jaringan WiFi"
            StatusLevel.BAHAYA -> "Bahaya: jaringan WiFi"
            else -> "Peringatan: jaringan WiFi"
        }
        val contentText = "Terhubung ke $ssid. Buka Susnet untuk melihat detail"

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_triangle)
            .setContentTitle("Peringatan Keamanan WiFi")
            .setContentText("Buka Susnet untuk melihat detail")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_triangle)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        notificationManager?.notify(key.hashCode(), notification)
        Log.d("SusnetAudit", "Notification sent for key=$key, title=$title")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Peringatan WiFi"
            val descriptionText = "Notifikasi peringatan keamanan jaringan WiFi"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
