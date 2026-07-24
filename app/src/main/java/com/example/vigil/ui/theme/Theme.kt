// src/main/java/com/example/vigil/ui/theme/Theme.kt
package com.example.vigil.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppDarkColorScheme = darkColorScheme(
    primary = VigilDirAAcid,
    onPrimary = VigilDirABg,
    primaryContainer = VigilPrimaryContainer,
    onPrimaryContainer = VigilTextPrimary,

    secondary = VigilSuccess,
    onSecondary = VigilOnPrimary,
    secondaryContainer = VigilSuccessContainer,
    onSecondaryContainer = VigilSuccess,

    tertiary = VigilInfo,
    onTertiary = VigilOnPrimary,
    tertiaryContainer = VigilInfoContainer,
    onTertiaryContainer = VigilInfo,

    error = VigilError,
    onError = VigilOnPrimary,
    errorContainer = VigilErrorContainer,
    onErrorContainer = VigilError,

    background = VigilDirABg,
    onBackground = VigilDirAInk,

    surface = VigilDirABg,
    onSurface = VigilDirAInk,
    surfaceVariant = VigilSurfaceVariant,
    onSurfaceVariant = VigilDirADim,

    outline = VigilDirAFaint,
    outlineVariant = VigilDirALine,
    scrim = VigilDirABg.copy(alpha = 0.8f)
)

private val AppLightColorScheme = lightColorScheme(
    primary = VigilDirAAcid,
    onPrimary = VigilDirABg,
    primaryContainer = VigilPrimaryContainer,
    onPrimaryContainer = VigilTextPrimary,

    secondary = VigilSuccess,
    onSecondary = VigilOnPrimary,
    secondaryContainer = VigilSuccessContainer,
    onSecondaryContainer = VigilSuccess,

    tertiary = VigilInfo,
    onTertiary = VigilOnPrimary,
    tertiaryContainer = VigilInfoContainer,
    onTertiaryContainer = VigilInfo,

    error = VigilError,
    onError = VigilOnPrimary,
    errorContainer = VigilErrorContainer,
    onErrorContainer = VigilError,

    background = VigilDirABg,
    onBackground = VigilDirAInk,

    surface = VigilDirABg,
    onSurface = VigilDirAInk,
    surfaceVariant = VigilSurfaceVariant,
    onSurfaceVariant = VigilDirADim,

    outline = VigilDirAFaint,
    outlineVariant = VigilDirALine,
    scrim = VigilDirABg.copy(alpha = 0.8f)
)

@Composable
fun VigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VigilDirABg.toArgb()
            window.navigationBarColor = VigilDirABg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
