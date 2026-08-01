// src/main/java/com/example/vigil/ui/AppDestinations.kt
package com.example.vigil.ui

/**
 * 应用的导航目的地路由。
 * 单页化后只剩主屏 + 应用过滤页（不再有底部导航）。
 */
object AppDestinations {
    // 单页主屏（「A · 一线」）
    const val Main = "main"

    // 应用过滤屏幕（不在主导航，从主屏进入）
    const val AppFilter = "app_filter"

    // 报警记录屏幕（不在主导航，从主屏设置 Sheet 进入）
    const val AlertHistory = "alert_history"

    // 铃声库屏幕（不在主导航，从主屏设置 Sheet 进入）
    const val RingtoneLibrary = "ringtone_library"
}
