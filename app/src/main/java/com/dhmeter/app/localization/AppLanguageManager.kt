package com.dropindh.app.localization

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
        context.findActivity()?.let { activity ->
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
        val language = context.resources.configuration.locales.get(0)?.language ?: LANGUAGE_ES
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
        Locale.setDefault(Locale(languageCode))
    }

    private fun normalize(languageCode: String): String {
        return if (languageCode.lowercase(Locale.US).startsWith(LANGUAGE_ES)) {
            LANGUAGE_ES
        } else {
            LANGUAGE_EN
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

