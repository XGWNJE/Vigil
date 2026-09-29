# 实现与代码索引

本页记录 Vigil 的实现入口与持久化约束；操作规则见 [AGENTS.md](../AGENTS.md)。

## 构建与版本

- 构建：`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- 版本号：`app/build.gradle.kts` 的 `versionCode`/`versionName`

## 核心代码

以下路径均相对仓库根目录。

- `app/src/main/java/com/example/vigil/MyNotificationListenerService.kt` — 监听/匹配/FIFO 调度/播放/唤醒锁/报警恢复/绑定看门狗（心跳中检测断连，触发 `ListenerRecovery` 快速自愈）；不同关键词排队、同来源同关键词聚合，结束当前项时原子写记录并推进队首；循环次数有限档位不用 isLooping，改 OnCompletion 手动续播计数（每次续播重新 acquire wakelock 续期），到数自动结束并广播状态变化；响铃期间同一前台服务通知 ID 切换到高优先级报警渠道，点击携带持久化 alertId/关键词进入处理，结束后切回普通监听通知；命中后按延时策略决定「立即入队」还是「落一条待触发项」（`scheduled_alerts`），到期判定的三层兜底 = AlarmManager 闹钟 / 服务内 Handler 定时器 / 心跳每 30s 扫描，均以「待触发项是否已被移除」做幂等判据
- `app/src/main/java/com/example/vigil/DelayedAlertScheduler.kt` — 延时报警到期时刻计算（固定延时按墙钟相加；每日定点取严格晚于命中时刻的最近时间点，当天全过则取次日第一个）与 AlarmManager 调度：有精确闹钟权限用 `setExactAndAllowWhileIdle`，否则回落 `setAndAllowWhileIdle`（见[平台兼容经验](platform.md)）；并负责跳系统「闹钟和提醒」授权页
- `app/src/main/java/com/example/vigil/DelayedAlertReceiver.kt` — 延时报警到期闹钟的接收方：收到系统闹钟后 `startForegroundService` 拉起服务并把到期 id 交给服务处理（服务不在时由心跳扫描补触发）
- `app/src/main/java/com/example/vigil/ListenerRecovery.kt` — 监听绑定自愈（快速自愈：requestRebind → 短观察 → 完整重连序列 → 无效即标记 `listener_recovery_failed`），Service 与 MainActivity 共用
- `app/src/main/java/com/example/vigil/RingtoneLibrary.kt` — 铃声库（P2 自定义铃声来源）+ 内置预设（随 APK 打包）：导入 SAF 音频复制到 `filesDir/ringtones/`、录音（MediaRecorder m4a/AAC）、试听、删除；铃声值约定 `content://` = 系统铃声 / `android.resource://<pkg>/raw/<name>` = 内置预设（res/raw WAV，不可删除，owner 提供 TTS 语音，MP3 因质量未采用）/ 其他非空 = 库内文件绝对路径 / null = 系统默认闹钟；`resolve()` 解析播放数据源（预设经 AssetFileDescriptor 播放，见[平台兼容经验](platform.md)），文件缺失回落并写日志；试听状态变化经 `onPreviewStateChanged` 回调刷新 UI
- `app/src/main/java/com/example/vigil/SharedPreferencesHelper.kt` — 全部持久化（关键词、默认铃声、关键词级铃声/循环次数映射 `keyword_ringtones`/`keyword_loop_counts`、全局默认循环次数 `default_loop_count`（1–10；旧版无限/越界值迁移到范围内）、报警 FIFO 队列 `alert_queue`（上限 20，含铃声 URI/loopLimit/已播次数/来源应用/聚合次数，兼容迁移旧 pending）、重复提醒间隔 `keyword_repeat_interval_ms` 与最近触发记录、报警历史 `alert_history`（JSON，上限 100 条）、listener_connected 绑定状态、延时报警默认策略 `default_delay_policy` 与关键词覆盖 `keyword_delay_policies`（JSON：mode=IMMEDIATE/FIXED/SCHEDULED + fixedDelayMs（1 秒 ~ 24 小时，UI 用时/分/秒三格精确输入）+ times 当日分钟数）、待触发延时项 `scheduled_alerts`（上限 20，含到期墙钟时刻；与 `alert_queue` 分开存，因为队列项一入队就响铃））
- `app/src/main/java/com/example/vigil/PermissionUtils.kt` — 权限检查与引导
- `app/src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt` — 服务状态/心跳/报警弹窗状态
- `app/src/main/java/com/example/vigil/VigilLogger.kt` — 持久化诊断日志（filesDir/logs/vigil.log，1MB 滚动双文件；每条立即 flush）+ 导出拼接（诊断头 + old + 当前），主屏设置 Sheet「导出日志」行经 FileProvider 分享；不写通知正文
- `app/src/main/java/com/example/vigil/VigilEventBus.kt` — 进程内事件总线（SharedFlow，报警确认携带 alertId，状态变化后 UI 必须以持久化队首为准；replay 语义与持久化兜底要求见[项目细则第 5 条](project-rules.md)）
- `app/src/main/java/com/example/vigil/ui/settings/SettingsViewModel.kt` — 关键词/铃声/应用过滤/延时策略状态与持久化；过滤列表排序：已勾选 → 用户应用优先 → 名称（`appListComparator`，初始加载与勾选切换共用）；延时策略区分「全局默认」与「关键词覆盖」（覆盖为 null 即跟随默认），并暴露精确闹钟授权状态与待触发延时项清单（查看/取消）
- `app/src/main/java/com/example/vigil/MainActivity.kt` — Compose 根宿主，生命周期管理，服务启停，报警弹窗承载；冷启动触发自动更新检查，并处理「安装未知应用」权限与安装调起
- `app/src/main/java/com/example/vigil/UpdateChecker.kt` — GitHub 渠道自动更新：`GET /releases/latest` 解析最新版（版本号/发版说明/APK 资产直链），耐用的版本号比较（`isNewer`），APK 下载到 cacheDir、FileProvider 授权调起系统安装。刻意用内置 `HttpURLConnection` + `org.json`，不引第三方库（保极致轻量）。debug 构建可用 `debug_update_api_base`（SharedPreferences）覆盖 API 基址给本地模拟（生产恒访问 GitHub 且仅 HTTPS）
- `app/src/main/java/com/example/vigil/UpdateViewModel.kt` — 自动更新状态机（冷启动/手动检查、更新弹窗、下载进度、安装就绪）；已点「稍后」的版本记入 `last_dismissed_update_version`，冷启动不再重复提示
## 界面与设计

- UI 页面：`MainScreen`（涟漪状态首页；设置 Sheet 双入口：顶栏齿轮 + 上滑手势（底部发丝线把手常驻，首次启动显示「上滑打开设置」，Sheet 打开过一次即收起）；Sheet 内容：关键词、铃声、循环次数、重复提醒间隔、延时报警（+ 有排定项时才出现的「待触发延时报警」行）、应用过滤、权限三级分组（REQUIRED 通知使用权 / RECOMMENDED 电池白名单 + 锁定任务卡片引导（「不再提示」后整行隐藏，`lock_task_tip_dismissed`）+ 精确闹钟（仅「有延时策略且未授权」时出现）/ OPTIONAL 自启动管理、后台运行）、导出日志、开源地址、检查更新（版本号文本，点击触发手动检查）、`AppFilterScreen`（应用过滤全屏页：搜索、SYS 标记、多选、勾选置顶）、`KeywordAlertDialog`（命中全屏弹窗，确认停铃，内容居中避开导航栏）、`KeywordConfigDialog` 内可逐关键词覆盖延时策略、`DelayPolicyDialog`/`ScheduledAlertsDialog`（延时策略选择与待触发清单取消）、`PermissionGuideDialog`（权限引导确认弹窗，确认后跳系统设置）、`ui/dialogs/UpdateDialog.kt`（自动更新弹窗：发现新版本展示发版说明 + 更新/稍后；已最新 / 无法访问 GitHub / 异常 各有提示；下载进度）
- 图标资产：launcher icon 内容不得顶边——缩放约 75% 居中、四边预留 ≥15% 安全边距，导出 mipmap 前做圆角 mask 预演（系统圆角 mask 会裁切顶边内容）。此约束由 owner 于 2026-07-30 验收。
- 设计主题「一线」：极简深色、发丝线分区、无卡片、单一强调色。背景 #0A0A0B / 文字 #EAEAE7 / 分割线 #1F1F23 / 主色 #E4FF54 酸橙绿 / 警示 #FFB020 琥珀

## 应用可见性

应用过滤通过 launcher intent 查询桌面可见应用，不申请 `QUERY_ALL_PACKAGES`。Android 11 及以上须在 `AndroidManifest.xml` 的 `<queries>` 块声明该 intent，否则列表可能只显示少量应用。
