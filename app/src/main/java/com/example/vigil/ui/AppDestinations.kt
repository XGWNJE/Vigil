// src/main/java/com/example/vigil/ui/AppDestinations.kt
package com.example.vigil.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes // 导入 StringRes
import com.example.vigil.R // *** 添加这行导入语句 ***
/**
 * 定义应用的导航目的地（屏幕）。
 * 使用密封类来限制可能的目的地，并包含路由字符串和底部导航图标。
 */
sealed class AppDestinations(
    val route: String,
    val icon: ImageVector,
    @StringRes val titleResId: Int // 使用 StringRes 引用字符串资源ID
) {
    // 监控屏幕
    // 使用您提供的 R.string.environment_check_label 作为标题资源的示例
    object Monitoring : AppDestinations("monitoring", Icons.Default.Warning, R.string.environment_check_label) // 使用 Warning 图标作为监控占位符，关联环境检测的字符串
    // 设置屏幕
    // 使用您提供的 R.string.permissions_settings_title 作为标题资源的示例
    object Settings : AppDestinations("settings", Icons.Default.Settings, R.string.permissions_settings_title) // 关联权限设置的字符串
}

/**
 * 应用的所有导航目的地列表，用于底部导航栏。
 */
val BottomNavDestinations = listOf(
    AppDestinations.Monitoring,
    AppDestinations.Settings
)
