package com.ingpsy.designate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager


class App: Application() {
    override fun onCreate() {
        super.onCreate()

        // set channel for notifications
        val designateChannel = NotificationChannel(
            "social_media_monitoring_channel",
            "Social_Media_Monitoring_Channel",
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(designateChannel)
    }

}