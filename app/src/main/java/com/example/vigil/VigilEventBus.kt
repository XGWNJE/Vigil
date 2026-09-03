package com.example.vigil

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 应用级事件总线，替代废弃的 LocalBroadcastManager。
 * 使用 SharedFlow(extraBufferCapacity=1) 保证即使收集方未就绪也不丢失最新事件。
 */
object VigilEventBus {
    /**
     * 服务心跳：Service → ViewModel，每 30s 发一次，payload 为发射时刻的 SystemClock.elapsedRealtime()。
     * replay=1 让冷启动的 ViewModel/UI 立即拿到最近一次心跳并据时间戳计算真实年龄，
     * 避免无 replay 时必须空等下一个 30s 周期、长时间停留在"连接中"状态。
     * 心跳不是关键事件，年龄由时间戳判定，陈旧 replay 不会掩盖服务已死（超 45s 仍判 HEARTBEAT_TIMEOUT）。
     */
    val heartbeat = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)

    /** 服务连接状态：Service → ViewModel，true=已连接，false=已断开 */
    val serviceStatus = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /**
     * 监听自动重连失败标记：Service/看门狗 → UI。
     * 完整重连序列也无效时置 true，UI 据此展示"重新授权"引导；
     * 冷启动兜底读 SharedPreferences（无 replay）。
     */
    val listenerRecoveryFailed = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /** 关键词报警事件：Service → ViewModel */
    val keywordAlert = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 1)

    /** 用户确认报警：ViewModel → Service，触发停止铃声 */
    /** UI 确认指定报警；alertId 防止迟到/重复点击误结束下一条。 */
    val alertConfirmed = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 报警队列发生变化；UI 收到后从持久化队首重新对账。 */
    val alertStateChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

data class AlertEvent(
    val alertId: String,
    val keyword: String,
    val sourceApp: String?,   // 来源应用名称（非包名）
    val snippet: String?,     // 触发报警的通知文本片段
    val queueSize: Int = 1,
    val occurrenceCount: Int = 1
)
