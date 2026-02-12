package com.dhmeter.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dhmeter.app.localization.AppLanguageManager
import com.dhmeter.app.ui.i18n.tr
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DHMeterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.applySavedLanguage(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            tr(this, "Recording", "Grabacion"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = tr(
                this@DHMeterApplication,
                "Shows when a run is being recorded",
                "Muestra cuando se esta grabando una bajada"
            )
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            tr(this, "Alerts", "Alertas"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = tr(
                this@DHMeterApplication,
                "Important alerts about your runs",
                "Alertas importantes sobre tus bajadas"
            )
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
