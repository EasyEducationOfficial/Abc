package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.service.MediaObserverService
import com.example.service.NotificationCaptureService

class MsgVaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Start background media observer if storage / SAF access is granted
        try {
            MediaObserverService.startService(this)
        } catch (ignored: Exception) {}

        // Ensure notification listener service binding is primed
        try {
            NotificationCaptureService.ensureServiceBound(this)
        } catch (ignored: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel for background observer service
            val serviceChannel = NotificationChannel(
                MediaObserverService.CHANNEL_ID,
                "MediaVault Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors messaging folders in real-time to preserve incoming photos, videos, and voice notes"
                setShowBadge(false)
            }

            // Channel for test/alert notifications
            val alertChannel = NotificationChannel(
                "msgvault_alerts",
                "MsgVault Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Status alerts and simulated test notifications"
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}
