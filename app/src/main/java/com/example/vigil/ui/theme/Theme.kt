// src/main/java/com/example/vigil/ui/theme/Theme.kt
package com.example.vigil.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme // *** 更改为 Material 3 ***
import androidx.compose.material3.darkColorScheme // *** 更改为 Material 3 ***
import androidx.compose.material3.dynamicDarkColorScheme // M3 动态颜色
import androidx.compose.material3.dynamicLightColorScheme // M3 动态颜色
import androidx.compose.material3.lightColorScheme // *** 更改为 Material 3 ***
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color // 这个保持不变
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material 3 颜色方案 (基于您之前定义的颜色，但适配 M3 的命名)
// 您可以从 Material Theme Builder (https://m3.material.io/theme-builder) 生成更完整的 M3 颜色方案

private val AppDarkColorScheme = darkColorScheme(
    primary = Gray800, // 主色
    onPrimary = Color.White, // 在主色上的文字/图标颜色
    primaryContainer = Gray900, // 主色容器
    onPrimaryContainer = Color.White, // 在主色容器上的文字/图标颜色

    secondary = Teal200, // 次色
    onSecondary = Color.Black, // 在次色上的文字/图标颜色
    secondaryContainer = Teal700, // 次色容器 (示例)
    onSecondaryContainer = Color.Black, // 在次色容器上的文字/图标颜色 (示例)

    tertiary = Purple200, // 第三色 (示例)
    onTertiary = Color.Black, // (示例)
    tertiaryContainer = Purple700, // (示例)
    onTertiaryContainer = Color.White, // (示例)

    error = RedError, // 错误颜色 (需要定义 RedError)
    onError = Color.White,
    errorContainer = RedErrorContainer, // (需要定义 RedErrorContainer)
    onErrorContainer = Color.Black,

    background = Color.Black, // 背景色
    onBackground = Color.White, // 在背景色上的文字/图标颜色

    surface = Gray800, // 表面色 (卡片，Sheet等)
    onSurface = Color.White, // 在表面色上的文字/图标颜色

    surfaceVariant = Gray900, // 表面变体 (用于区分层次)
    onSurfaceVariant = Gray200, //

    outline = Gray500, // 轮廓线颜色
    inverseOnSurface = Gray900, // (用于特殊情况的反色)
    inverseSurface = Gray200,   // (用于特殊情况的反色)
    inversePrimary = Purple500, // (用于特殊情况的反色)
    surfaceTint = Gray800, // 表面着色 (通常与 primary 相同)
    outlineVariant = Gray500, // 轮廓线变体
    scrim = Color.Black.copy(alpha = 0.32f) // 遮罩层颜色
)

private val AppLightColorScheme = lightColorScheme(
    primary = Gray200, // 主色
    onPrimary = Color.Black,
    primaryContainer = Gray500,
    onPrimaryContainer = Color.Black,

    secondary = Teal700,
    onSecondary = Color.White,
    secondaryContainer = Teal200,
    onSecondaryContainer = Color.Black,

    tertiary = Purple500,
    onTertiary = Color.White,
    tertiaryContainer = Purple200,
    onTertiaryContainer = Color.Black,

    error = RedErrorDark, // (需要定义 RedErrorDark)
    onError = Color.White,
    errorContainer = RedErrorContainerDark, // (需要定义 RedErrorContainerDark)
    onErrorContainer = Color.Black,

    background = Color.White,
    onBackground = Color.Black,

    surface = Color.White,
    onSurface = Color.Black,

    surfaceVariant = Gray200,
    onSurfaceVariant = Gray800,

    outline = Gray500,
    inverseOnSurface = Gray200,
    inverseSurface = Gray900,
    inversePrimary = Purple700,
    surfaceTint = Gray200,
    outlineVariant = Gray800,
    scrim = Color.Black.copy(alpha = 0.32f)
)

@Composable
fun VigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, // 可以设为 false 以禁用动态颜色
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppDarkColorScheme
        else -> AppLightColorScheme
    }

    // 更新状态栏和导航栏颜色 (可选，但推荐用于 M3 的沉浸式体验)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // 或 colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb() // 或 colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme( // *** 使用 Material 3 的 MaterialTheme ***
        colorScheme = colorScheme,
        typography = Typography, // Typography 也需要适配 M3 (见 Type.kt)
        shapes = Shapes, // Shapes 也需要适配 M3 (见 Shape.kt)
        content = content
    )
}
