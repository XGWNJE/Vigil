// src/main/java/com/example/vigil/ui/AppDestinations.kt
package com.example.vigil.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.ui.res.vectorResource
import androidx.compose.runtime.Composable
import com.example.vigil.R

/**
 * 定义应用的导航目的地（屏幕）。
 * 使用密封类来限制可能的目的地，并包含路由字符串和底部导航图标。
 */
sealed class AppDestinations(
    val route: String,
    val iconResId: Int, // 使用资源ID而不是ImageVector
    @StringRes val titleResId: Int // 使用 StringRes 引用字符串资源ID
) {
    // 通知屏幕
    object Monitoring : AppDestinations(
        "monitoring", 
        R.drawable.ic_notification_icon, 
        R.string.notification_configuration_title
    )
    // 设置屏幕
    object Settings : AppDestinations(
        "settings", 
        R.drawable.ic_settings, 
        R.string.permissions_settings_title
    )

    @Composable
    fun getIcon(): ImageVector {
        return ImageVector.vectorResource(id = iconResId)
    }
}

/**
 * 应用的所有导航目的地列表，用于底部导航栏。
 */
val BottomNavDestinations = listOf(
    AppDestinations.Monitoring,
    AppDestinations.Settings
)
