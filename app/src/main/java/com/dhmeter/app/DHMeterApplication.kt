package com.dhmeter.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DHMeterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when a run is being recorded"
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important alerts about your runs"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(recordingChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }

    companion object {
        const val CHANNEL_RECORDING = "recording_channel"
        const val CHANNEL_ALERTS = "alerts_channel"
    }
}
