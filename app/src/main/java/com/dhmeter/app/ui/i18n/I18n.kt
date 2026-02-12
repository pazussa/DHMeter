package com.dhmeter.app.ui.i18n

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun tr(en: String, es: String): String {
    val context = LocalContext.current
    return tr(context, en, es)
}

fun tr(context: Context, en: String, es: String): String {
    val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales.get(0)?.language
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale.language
    } ?: "en"

    return if (language.lowercase(Locale.US).startsWith("es")) es else en
}
