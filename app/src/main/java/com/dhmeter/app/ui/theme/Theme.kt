package com.dhmeter.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val DarkColorScheme = darkColorScheme(
    primary = Orange500,
    onPrimary = Color(0xFF1A1207),
    primaryContainer = Color(0xFF4A2A0A),
    onPrimaryContainer = Color(0xFFFFD8AF),
    secondary = Color(0xFF8FE35A),
    onSecondary = Color(0xFF0C1708),
    secondaryContainer = Color(0xFF233819),
    onSecondaryContainer = Color(0xFFCEF9AE),
    tertiary = Teal500,
    onTertiary = Color(0xFF02131A),
    tertiaryContainer = Color(0xFF083240),
    onTertiaryContainer = Color(0xFFA6F0FF),
    background = DarkBackground,
    onBackground = Color(0xFFEAF1FB),
    surface = DarkSurface,
    onSurface = Color(0xFFF4F8FF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC3CCDC),
    outline = Color(0xFF6A778D),
    outlineVariant = Color(0xFF404B5E),
    error = RedNegative,
    onError = Color(0xFF250202),
    errorContainer = Color(0xFF541010),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Orange700,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Orange200,
    onPrimaryContainer = Color(0xFF321A04),
    secondary = Teal700,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8EFF7),
    onSecondaryContainer = Color(0xFF05222B),
    tertiary = Color(0xFF2E8FB6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8F4FF),
    onTertiaryContainer = Color(0xFF002230),
    background = Color(0x00000000),
    onBackground = Color(0xFFEAF1FB),
    surface = DarkSurface,
    onSurface = Color(0xFFF4F8FF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC3CCDC),
    outline = Color(0xFF6A778D),
    outlineVariant = Color(0xFF404B5E),
    error = RedNegative,
    onError = Color(0xFF250202)
)

@Composable
fun DHMeterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Disable dynamic colors to keep brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = false
            WindowInsetsControllerCompat(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DHMeterShapes,
        content = content
    )
}
