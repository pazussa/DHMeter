package com.dropindh.app.ui.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.dropindh.app.localization.AppLanguageManager
import java.util.Locale

@Composable
fun tr(en: String, es: String): String {
    LocalConfiguration.current
    val context = LocalContext.current
    return tr(context, en, es)
}

fun tr(context: Context, en: String, es: String): String {
    val savedLanguage = runCatching {
        AppLanguageManager.getSavedLanguage(context)
    }.getOrNull()
    val appLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language
    val contextLocale = context.resources.configuration.locales.get(0)?.language
    val language = savedLanguage ?: appLocale ?: contextLocale ?: Locale.getDefault().language

    return if (language.lowercase(Locale.US).startsWith("es")) es else en
}

