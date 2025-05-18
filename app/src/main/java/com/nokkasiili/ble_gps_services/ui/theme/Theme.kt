package com.nokkasiili.ble_gps_services.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


// Dark Hacker Theme (Use this instead of default DarkColorScheme)
private val HackerDarkColorScheme = darkColorScheme(
    primary = NeonGreen,          // Neon Green for primary actions and highlights
    secondary = NeonBlue,         // Cyan for secondary elements
    tertiary = NeonPurple,        // Purple for tertiary accents
    background = TextDark,     // Pure black background
    surface = DarkSurface,        // Dark surface
    error = ErrorRed,             // Error Red
    onPrimary = TextDark,         // Black text on bright primary
    onSecondary = TextDark,       // Black text on bright secondary
    onTertiary = TextDark,        // Black text on bright tertiary
    onBackground = TextLight,     // Light gray text on dark background
    onSurface = TextLight,        // Light gray text on dark surface
    onError = TextDark,           // Black text on bright error
)

// Original Dark Theme (keep for reference/compatibility)
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Original Light Theme (keep for reference/compatibility)
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

// In your Theme.kt file, update your app theme like this:
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    hackerTheme: Boolean = true, // Add this parameter to enable/disable hacker theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Use our custom hacker theme if enabled, otherwise fall back to regular themes
        hackerTheme -> HackerDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}