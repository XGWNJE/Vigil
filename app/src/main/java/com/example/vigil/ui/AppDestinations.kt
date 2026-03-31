// src/main/java/com/example/vigil/ui/AppDestinations.kt
package com.example.vigil.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.example.vigil.R

/**
 * 定义应用的导航目的地（屏幕）。
 * 使用密封类来限制可能的目的地，并包含路由字符串和底部导航图标。
 */
sealed class AppDestinations(
    val route: String,
    val icon: ImageVector,
    @StringRes val titleResId: Int
) {
    // 通知屏幕
    object Monitoring : AppDestinations(
        "monitoring",
        Icons.Filled.Sensors,
        R.string.bottom_nav_monitoring
    )
    // 设置屏幕
    object Settings : AppDestinations(
        "settings",
        Icons.Filled.Settings,
        R.string.permissions_settings_title
    )

    // 应用过滤屏幕（不在底部导航栏，从 Settings 页面进入）
    object AppFilter : AppDestinations(
        "app_filter",
        Icons.Filled.Settings,
        R.string.app_filter_settings_title
    )
}

/**
 * 应用的所有导航目的地列表，用于底部导航栏。
 */
val BottomNavDestinations = listOf(
    AppDestinations.Monitoring,
    AppDestinations.Settings
)
