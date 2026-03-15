package com.lemn.app.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Clean modern palette: yellow + blue (no terminal green)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF2C94C),        // Warm yellow
    onPrimary = Color(0xFF1B1B1F),
    secondary = Color(0xFF4DA3FF),      // Soft blue
    onSecondary = Color(0xFF0B1B2B),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF151923),
    onSurface = Color(0xFFE8EDF2),
    error = Color(0xFFB8860B),          // Amber (avoid emergency red outside Emergency UI)
    onError = Color(0xFF1B1B1F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFF4C542),        // Warm yellow
    onPrimary = Color(0xFF1E1E1E),
    secondary = Color(0xFF2F80ED),      // Blue
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1E1E1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1E1E),
    error = Color(0xFFC58B1C),          // Amber (avoid emergency red outside Emergency UI)
    onError = Color(0xFF1E1E1E)
)

// Emergency palette: red + green only for Emergency tab UI
private val EmergencyDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE45757),        // Red
    onPrimary = Color(0xFF1B1B1F),
    secondary = Color(0xFF2ECC71),      // Green
    onSecondary = Color(0xFF0B1B2B),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF171A22),
    onSurface = Color(0xFFE8EDF2),
    error = Color(0xFFE45757),
    onError = Color(0xFF1B1B1F)
)

private val EmergencyLightColorScheme = lightColorScheme(
    primary = Color(0xFFE04B4B),        // Red
    onPrimary = Color.White,
    secondary = Color(0xFF2FAE63),      // Green
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1E1E1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1E1E),
    error = Color(0xFFE04B4B),
    onError = Color.White
)

internal fun emergencyColorScheme(isDark: Boolean) =
    if (isDark) EmergencyDarkColorScheme else EmergencyLightColorScheme

@Composable
fun BitchatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

