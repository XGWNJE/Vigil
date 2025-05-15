// src/main/java/com/example/vigil/ui/theme/Shape.kt
package com.example.vigil.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes // 导入 Material 2 的 Shapes
import androidx.compose.ui.unit.dp

// 定义您应用的形状
// 您可以根据您的设计偏好进行修改
val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp) // 可以设置更大的圆角来增加设计感
)

/*
// 示例：定义一个自定义形状
val CustomShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
*/
