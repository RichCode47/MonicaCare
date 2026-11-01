package com.monicacare.app.core.designsystem.theme

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RosePinkPrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = LightBlush,
    onPrimaryContainer = RosePinkDark,
    secondary = TealSecondary,
    onSecondary = SurfaceWhite,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealDark,
    background = WarmWhiteBackground,
    onBackground = DarkCharcoalText,
    surface = SurfaceWhite,
    onSurface = DarkCharcoalText,
    surfaceVariant = LightBlush,
    onSurfaceVariant = DarkCharcoalText,
    error = EmergencyRed,
    errorContainer = EmergencyRedContainer,
    onError = SurfaceWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = RosePinkPrimaryDark,
    onPrimary = DarkBackground,
    primaryContainer = RosePinkDark,
    onPrimaryContainer = LightBlush,
    secondary = TealSecondaryDark,
    onSecondary = DarkBackground,
    secondaryContainer = TealDark,
    onSecondaryContainer = TealContainer,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkText,
    error = EmergencyRed,
    errorContainer = EmergencyRedContainer,
    onError = SurfaceWhite
)

@Composable
fun MonicaCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve MonicaCare brand colors by default
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
