package com.dropindh.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dropindh.app.community.FirebaseBootstrap
import com.dropindh.app.localization.AppLanguageManager
import com.dropindh.app.monetization.EventTracker
import com.dropindh.app.ui.i18n.tr
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DHMeterApplication : Application() {

    @Inject
    lateinit var eventTracker: EventTracker

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.applySavedLanguage(this)
        FirebaseBootstrap.initialize(this)
        eventTracker.trackInstallIfNeeded()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            tr(this, "Recording", "Grabación"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = tr(
                this@DHMeterApplication,
                "Shows when a run is being recorded",
                "Muestra cuando se está grabando una bajada"
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

