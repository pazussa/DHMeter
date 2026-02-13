package com.dropindh.app.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun tr(en: String, es: String): String {
    val context = LocalContext.current
    return tr(context, en, es)
}

fun tr(context: Context, en: String, es: String): String {
    val language = context.resources.configuration.locales.get(0)?.language ?: "en"

    return if (language.lowercase(Locale.US).startsWith("es")) es else en
}

