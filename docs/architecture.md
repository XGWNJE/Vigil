# 实现与代码索引

本页记录当前源码入口；协作规则见 [AGENTS.md](../AGENTS.md)，验证步骤见[设备手册](testing.md)。

## 构建与版本

- 版本来源：`app/build.gradle.kts` 的 `versionCode` / `versionName`。
- 调试构建：`./gradlew :app:assembleDebug`，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- 更新与发布：[发布手册](release.md)。

## 报警链路

| 入口 | 当前职责 |
| --- | --- |
| `node/NotificationNodeService.kt` | 独立 specialUse 前台连接、通知范围与告警时间标准、实时事件落盘后确认、服务中断监测与重连；复用既有播放器 |
| `node/NotificationNodeSettings.kt` | 私有连接配置、WSS 校验与保存、告警时区持久化；节点凭据排除云备份及设备转移 |
| `node/AlarmTime.kt` / `ui/AlarmTime.kt` | 按服务标准格式化报警时间，设置推送后重绘列表、历史与弹窗 |
| `MyNotificationListenerService.kt` | 通知过滤与内容匹配、队列调度、有限次数播放、前台通知、监听恢复；同一通知内容更新后重新匹配，接收失败不占用去重记录 |
| `SharedPreferencesHelper.kt` | 保存设置、队列、延时项和历史；各实例共用关键写锁，延时转队列一次提交，确认结束与历史写入一次提交 |
| `DelayedAlertScheduler.kt` / `DelayedAlertReceiver.kt` | 计算固定延时或下一个每日时间点，安排系统闹钟并通知服务处理到期项；无精确授权时使用非精确闹钟 |
| `ListenerRecovery.kt` | 尝试重新绑定与组件重连；失败后保留人工重试和重新授权入口 |
| `ui/monitoring/MonitoringViewModel.kt` | 服务状态、心跳年龄和持久化报警对话框；启动观察窗口结束仍无信号时显示超时，确认先保存再通知服务 |
| `VigilEventBus.kt` | 进程内刷新通知；除带发射时间的心跳外没有 replay，关键状态以持久化数据为准 |

报警队列与待触发列表各最多 20 项，历史最多 100 条。即时队列满时拒绝新增；到期延时项遇到队列满会保留重试。服务重建只恢复 30 分钟内且尚有播放次数的报警，超过到期时刻 12 小时的延时项会丢弃。系统调度、关机和厂商后台限制仍可能中断提醒。

重复提醒间隔在成功接收通知时记录，延时到期不再重新占用间隔。队列项保存稳定 ID、来源、触发时间、最多 100 字摘要、铃声和已播次数；历史保留 ID 与结束方式，不保存摘要。确认时服务即使没收到临时事件，也会通过持久化队列变化和心跳对账停止旧项。播放失败记录 `ERROR` 并处理下一项。

告警时间标准由 Web 控制台设置，支持北京时间（UTC+8）和 UTC（UTC+0）。通知节点在认证、重连和设置推送时同步并保存时区，最近报警、历史和弹窗使用该标准；弹窗显示完整日期与时区。未接入统一服务时沿用设备时区。切换标准只改变显示，不改变事件发生瞬间、有效期、设备系统时钟或每日定点策略的本机时区。

## 铃声与数据

- `RingtoneLibrary.kt`：导入、录音、试听和 6 条内置 WAV；系统铃声为 `content://`，内置预设为 `android.resource://`，自定义铃声为私有文件路径，`silent:` 表示静音，null 使用默认闹钟铃声。静音报警仍可在弹窗中确认。
- `ui/settings/SettingsViewModel.kt`：关键词、应用过滤、铃声、播放次数和延时策略；立即报警也是可保存的关键词覆盖，null 才表示跟随全局。每日定点必须至少有一个时间。
- `VigilLogger.kt`：私有日志与手动导出，不写通知正文；系统备份范围见[隐私政策](../PRIVACY.md)。
- 应用过滤启用后只处理选中应用；列表为空时不处理任何应用。应用列表只查询 launcher 可见应用，不申请 `QUERY_ALL_PACKAGES`。

## 界面与更新

- `MainActivity.kt`：恢复已启用的通知节点，Compose 导航、报警弹窗、权限引导与更新安装。报警期间暂缓显示更新弹窗或下载进度；报警结束后清除锁屏显示、屏幕唤醒等窗口标记。
- `ui/main/NotificationDashboard.kt`：接警首页、连接编辑、手机/平板导航；`MainScreen.kt` 提供关键词和声音/权限设置；铃声选择列表可滚动，系统选择器的关键词目标在屏幕旋转后恢复。
- `ui/settings/RingtoneLibraryScreen.kt` / `AppFilterScreen.kt` / `ui/history/AlertHistoryScreen.kt`：铃声库、应用过滤和记录；历史使用稳定 ID 并随持久化变化刷新。
- `UpdateChecker.kt` / `UpdateViewModel.kt`：GitHub 更新检查、下载和系统安装引导；数字版本段完整校验，支持 v 前缀与后缀。下载核对长度、资产大小、GitHub SHA-256（提供时）、包名、版本和当前签名；失败显示提示并清理临时文件。调试构建可使用本地更新接口，Release 使用 GitHub。

当前 UI 跟随系统浅色与深色：浅色背景 #F5F7F8、文字 #172326、强调 #087F83；深色背景 #10191B、文字 #EDF4F5、强调 #4FBCC1。全局颜色来自 MaterialTheme，平板宽度达到 840 dp 显示侧栏；窄屏使用紧凑导航。`ui/main/FrameProgress.kt` 保留报警与录音进度辅助。

启动图标为批准的位图资产；保留约 75% 内容宽度和至少 15% 安全边距，不重新绘制。后台弹窗与锁屏行为受 Android 和厂商策略限制，不承诺自动弹出全屏通知。

## 远程事件边界

`acceptRemoteAlert` 按稳定 ID 将事件和收件去重记录一次提交；未成功保存或队列已满不确认。远程事件不使用本机关键词聚合、冷却或延时，使用默认铃声与次数；30 秒实时期限只决定是否接收入队，不截断已接受报警的播放。重复投递不恢复已经确认的项。

两路接收使用独立开关。本机通知监听关闭时，通知节点仍可收件与播放，无需通知使用权；通知节点停用不会清除已接受队列。服务中断超过 45 秒后产生一次本地报警，恢复响应后允许下一次监测；设备省电、强制停止和长期后台行为仍需真机验证。
