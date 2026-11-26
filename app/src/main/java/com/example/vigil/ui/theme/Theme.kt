package com.example.vigil.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 暖黑石墨 + 琥珀金（Warm Graphite & Amber）色彩体系。
 * 深暖灰底 + 琥珀金待机信号 + 高级暖红报警态；浅色模式对应暖纸白 + 沉稳深琥珀。
 */
private val WarmGraphiteDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF5A524),
    onPrimary = Color(0xFF1A1205),
    primaryContainer = Color(0xFF332512),
    onPrimaryContainer = Color(0xFFFDE8C8),
    secondary = Color(0xFFA39B8F),
    onSecondary = Color(0xFF121110),
    background = Color(0xFF121110),
    onBackground = Color(0xFFF5F2EB),
    surface = Color(0xFF1C1A17),
    onSurface = Color(0xFFF5F2EB),
    surfaceVariant = Color(0xFF26231F),
    onSurfaceVariant = Color(0xFFA39B8F),
    outline = Color(0xFF4A443C),
    outlineVariant = Color(0xFF2E2A25),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFF381719),
    onErrorContainer = Color(0xFFFFD8DA)
)

private val WarmPaperLightColorScheme = lightColorScheme(
    primary = Color(0xFFC87A08),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDEFD8),
    onPrimaryContainer = Color(0xFF3B2304),
    secondary = Color(0xFF736B60),
    onSecondary = Color.White,
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFECE8E0),
    onSurfaceVariant = Color(0xFF736B60),
    outline = Color(0xFFCFC8BC),
    outlineVariant = Color(0xFFE5DFD5),
    error = Color(0xFFD9383E),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E6),
    onErrorContainer = Color(0xFF7A1418)
)

@Composable
fun VigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WarmGraphiteDarkColorScheme else WarmPaperLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
