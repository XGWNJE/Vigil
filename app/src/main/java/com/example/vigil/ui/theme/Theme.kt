// src/main/java/com/example/vigil/ui/theme/Theme.kt
package com.example.vigil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme // 导入 Material 2 的 MaterialTheme
import androidx.compose.material.darkColors // 导入 Material 2 的 darkColors
import androidx.compose.material.lightColors // 导入 Material 2 的 lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 定义一套简单的非 Material Design 颜色方案
// 您可以根据您的设计偏好进行修改
private val DarkColorPalette = darkColors(
    primary = Gray800, // 主色
    primaryVariant = Gray900, // 主色变体
    secondary = Teal200, // 次色
    background = Color.Black, // 背景色
    surface = Gray800, // 表面色 (卡片，Sheet等)
    onPrimary = Color.White, // 在主色上的文字/图标颜色
    onSecondary = Color.Black, // 在次色上的文字/图标颜色
    onBackground = Color.White, // 在背景色上的文字/图标颜色
    onSurface = Color.White // 在表面色上的文字/图标颜色
)

private val LightColorPalette = lightColors(
    primary = Gray200, // 主色
    primaryVariant = Gray500, // 主色变体
    secondary = Teal700, // 次色
    background = Color.White, // 背景色
    surface = Color.White, // 表面色
    onPrimary = Color.Black, // 在主色上的文字/图标颜色
    onSecondary = Color.White, // 在次色上的文字/图标颜色
    onBackground = Color.Black, // 在背景色上的文字/图标颜色
    onSurface = Color.Black // 在表面色上的文字/图标颜色

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

/**
 * 应用的自定义 Compose 主题。
 * 使用 MaterialTheme 作为基础，但应用我们自定义的颜色、字体和形状。
 * 这允许我们利用 Material 组件的基础结构，但完全控制视觉外观。
 */
@Composable
fun VigilTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    // 使用 MaterialTheme，但传入我们自定义的颜色、字体和形状
    MaterialTheme(
        colors = colors,
        typography = Typography, // 使用我们自定义的 Typography
        shapes = Shapes, // 使用我们自定义的 Shapes
        content = content
    )
}
