package com.dhmeter.app.localization

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "dhmeter_app_settings"
    private const val KEY_LANGUAGE = "language_code"

    const val LANGUAGE_EN = "en"
    const val LANGUAGE_ES = "es"

    fun applySavedLanguage(context: Context) {
        applyLanguage(context, getSavedLanguage(context), persist = false)
    }

    fun setLanguage(context: Context, languageCode: String) {
        applyLanguage(context, normalize(languageCode), persist = true)
        (context as? Activity)?.let { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.recreate()
            }
        }
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalize(
            prefs.getString(KEY_LANGUAGE, LANGUAGE_ES) ?: LANGUAGE_ES
        )
    }

    fun isSpanish(context: Context): Boolean {
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)?.language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        } ?: LANGUAGE_ES
        return normalize(language) == LANGUAGE_ES
    }

    private fun applyLanguage(context: Context, languageCode: String, persist: Boolean) {
        if (persist) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, languageCode)
                .apply()
        }

        val locales = LocaleListCompat.create(Locale(languageCode))
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun normalize(languageCode: String): String {
        return if (languageCode.lowercase(Locale.US).startsWith(LANGUAGE_ES)) {
            LANGUAGE_ES
        } else {
            LANGUAGE_EN
        }
    }
}
