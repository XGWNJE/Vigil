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
    primary = androidx.compose.ui.graphics.Color(0xFF4FBCC1), onPrimary = androidx.compose.ui.graphics.Color(0xFF10191B),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF113437), onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFEDF4F5),
    background = androidx.compose.ui.graphics.Color(0xFF10191B), onBackground = androidx.compose.ui.graphics.Color(0xFFEDF4F5),
    surface = androidx.compose.ui.graphics.Color(0xFF172326), onSurface = androidx.compose.ui.graphics.Color(0xFFEDF4F5),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF203033), onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9AADB1),
    outline = androidx.compose.ui.graphics.Color(0xFF66777A), outlineVariant = androidx.compose.ui.graphics.Color(0xFF2C3A3D)
)
private val AppLightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF087F83), onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE2F1F2), onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF172326),
    background = androidx.compose.ui.graphics.Color(0xFFF5F7F8), onBackground = androidx.compose.ui.graphics.Color(0xFF172326),
    surface = androidx.compose.ui.graphics.Color.White, onSurface = androidx.compose.ui.graphics.Color(0xFF172326),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFF3F4), onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF66777A),
    outline = androidx.compose.ui.graphics.Color(0xFF98A9AC), outlineVariant = androidx.compose.ui.graphics.Color(0xFFDFE6E7)
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
            window.statusBarColor = colorScheme.background.toArgb()
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
