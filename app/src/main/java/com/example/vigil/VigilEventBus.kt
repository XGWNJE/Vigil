package com.example.vigil

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 应用级事件总线，替代废弃的 LocalBroadcastManager。
 * 使用 SharedFlow(extraBufferCapacity=1) 保证即使收集方未就绪也不丢失最新事件。
 */
object VigilEventBus {
    /** 服务心跳：Service → ViewModel，每 30s 发一次 */
    val heartbeat = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 服务连接状态：Service → ViewModel，true=已连接，false=已断开 */
    val serviceStatus = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** 关键词报警事件：Service → ViewModel */
    val keywordAlert = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 1)

    /** 用户确认报警：ViewModel → Service，触发停止铃声 */
    val alertConfirmed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

data class AlertEvent(
    val keyword: String,
    val sourceApp: String?,   // 来源应用名称（非包名）
    val snippet: String?      // 触发报警的通知文本片段
)
