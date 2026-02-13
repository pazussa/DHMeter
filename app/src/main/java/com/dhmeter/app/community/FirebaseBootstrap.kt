package com.dropindh.app.community

import android.content.Context
import com.dropindh.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.util.concurrent.atomic.AtomicBoolean

object FirebaseBootstrap {
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context): Boolean {
        if (initialized.get()) return true
        if (!isConfigured()) return false
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            initialized.set(true)
            return true
        }

        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
            .build()

        FirebaseApp.initializeApp(context, options)
        initialized.set(true)
        return true
    }

    fun isReady(context: Context): Boolean {
        return initialized.get() || FirebaseApp.getApps(context).isNotEmpty()
    }

    fun isConfigured(): Boolean {
        return BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank() &&
            BuildConfig.FIREBASE_MESSAGING_SENDER_ID.isNotBlank()
    }
}

