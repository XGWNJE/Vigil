// src/main/java/com/example/vigil/ui/theme/Type.kt
package com.example.vigil.ui.theme

import androidx.compose.material.Typography // 导入 Material 2 的 Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 定义您应用的字体样式
// 您可以根据您的设计偏好进行修改
val Typography = Typography(
    body1 = TextStyle(
        fontFamily = FontFamily.Default, // 使用系统默认字体
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    h6 = TextStyle( // 示例：定义一个标题样式
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )
    /* Other default text styles to override
    button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
    */
)

/*
// 示例：使用自定义字体
val CustomFontFamily = FontFamily(
    Font(R.font.custom_font_regular),
    Font(R.font.custom_font_bold, FontWeight.Bold)
)

val CustomTypography = Typography(
    body1 = TextStyle(fontFamily = CustomFontFamily)
    // ... 其他样式
)
*/
