package com.dropindh.app.monetization

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventTracker @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun track(eventName: String, params: Map<String, String> = emptyMap()) {
        val normalizedEvent = normalize(eventName)
        val countKey = countKey(normalizedEvent)
        val count = prefs.getInt(countKey, 0) + 1

        prefs.edit()
            .putInt(countKey, count)
            .putLong(lastTimestampKey(normalizedEvent), System.currentTimeMillis())
            .apply()

        val payload = if (params.isEmpty()) {
            "event=$normalizedEvent count=$count"
        } else {
            "event=$normalizedEvent count=$count params=$params"
        }
        Log.i(TAG, payload)
    }

    fun trackInstallIfNeeded() {
        trackOneTime(MonetizationEvents.INSTALL)
    }

    fun trackOnboardingCompleteIfNeeded() {
        trackOneTime(MonetizationEvents.ONBOARDING_COMPLETE)
    }

    fun trackFirstRunCompleteIfNeeded() {
        trackOneTime(MonetizationEvents.FIRST_RUN_COMPLETE)
    }

    fun trackPaywallView() {
        track(MonetizationEvents.PAYWALL_VIEW)
    }

    fun getEventCount(eventName: String): Int {
        val normalizedEvent = normalize(eventName)
        return prefs.getInt(countKey(normalizedEvent), 0)
    }

    private fun trackOneTime(eventName: String) {
        val normalizedEvent = normalize(eventName)
        val onceKey = onceKey(normalizedEvent)
        if (prefs.getBoolean(onceKey, false)) return
        prefs.edit().putBoolean(onceKey, true).apply()
        track(normalizedEvent)
    }

    private fun normalize(eventName: String): String {
        return eventName
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "unknown_event" }
    }

    private fun countKey(eventName: String): String = "count_$eventName"

    private fun lastTimestampKey(eventName: String): String = "ts_$eventName"

    private fun onceKey(eventName: String): String = "once_$eventName"

    private companion object {
        const val TAG = "dropInDH-Events"
        const val PREFS_NAME = "dropin_dh_event_tracker"
    }
}

