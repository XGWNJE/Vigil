# 实现与代码索引

本页记录当前源码入口；协作规则见 [AGENTS.md](../AGENTS.md)，验证步骤见[设备手册](testing.md)。

## 构建与版本

- 版本来源：`app/build.gradle.kts` 的 `versionCode` / `versionName`。
- 调试构建：`./gradlew :app:assembleDebug`，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- 更新与发布：[发布手册](release.md)。

## 报警链路

| 入口 | 当前职责 |
| --- | --- |
| `MyNotificationListenerService.kt` | 通知过滤与内容匹配、队列调度、有限次数播放、前台通知、监听恢复；同一通知内容更新后重新匹配，接收失败不占用去重记录（保持根包以稳定组件名） |
| `data/SharedPreferencesHelper.kt` | 保存设置、队列、延时项和历史；各实例共用关键写锁，延时转队列一次提交，确认结束与历史写入一次提交 |
| `alert/DelayedAlertScheduler.kt` / `DelayedAlertReceiver.kt` | 计算固定延时或下一个每日时间点，安排系统闹钟并通知服务处理到期项；无精确授权时使用非精确闹钟（Receiver 保持根包） |
| `util/ListenerRecovery.kt` | 尝试重新绑定与组件重连；失败后保留人工重试和重新授权入口 |
| `ui/monitoring/MonitoringViewModel.kt` | 服务状态、心跳年龄和持久化报警对话框；启动观察窗口结束仍无信号时显示超时，确认先保存再通知服务 |
| `util/VigilEventBus.kt` | 进程内刷新通知；除带发射时间的心跳外没有 replay，关键状态以持久化数据为准 |

报警队列与待触发列表各最多 20 项，历史最多 100 条。即时队列满时拒绝新增；到期延时项遇到队列满会保留重试。服务重建只恢复 30 分钟内且尚有播放次数的报警，超过到期时刻 12 小时的延时项会丢弃。系统调度、关机和厂商后台限制仍可能中断提醒。

重复提醒间隔在成功接收通知时记录，延时到期不再重新占用间隔。队列项保存稳定 ID、来源、触发时间、最多 100 字摘要、铃声和已播次数；历史保留 ID 与结束方式，不保存摘要。确认时服务即使没收到临时事件，也会通过持久化队列变化和心跳对账停止旧项。播放失败记录 `ERROR` 并处理下一项。

## 铃声与数据

- `alert/RingtoneLibrary.kt`：导入、录音、试听和 6 条内置 WAV；系统铃声为 `content://`，内置预设为 `android.resource://`，自定义铃声为私有文件路径，`silent:` 表示静音，null 使用默认闹钟铃声。静音报警仍可在弹窗中确认。
- `ui/settings/SettingsViewModel.kt`：关键词、应用过滤、铃声、播放次数和延时策略；立即报警也是可保存的关键词覆盖，null 才表示跟随全局。每日定点必须至少有一个时间。
- `util/VigilLogger.kt` / `util/PermissionUtils.kt`：私有日志导出（不写通知正文）与通知/闹钟/录音权限检查；系统备份范围见[隐私政策](../PRIVACY.md)。
- 应用过滤启用后只处理选中应用；列表为空时不处理任何应用。应用列表只查询 launcher 可见应用，不申请 `QUERY_ALL_PACKAGES`。

## 界面与更新

- `MainActivity.kt`：Compose 导航、报警弹窗、权限引导与更新安装。报警期间暂缓显示更新弹窗或下载进度；报警结束后清除锁屏显示、屏幕唤醒等窗口标记。
- `ui/main/NotificationDashboard.kt`：监控首页、手机/平板导航；`MainScreen.kt` 提供关键词和声音/权限设置；铃声选择列表可滚动，系统选择器的关键词目标在屏幕旋转后恢复。
- `ui/settings/RingtoneLibraryScreen.kt` / `AppFilterScreen.kt` / `ui/history/AlertHistoryScreen.kt`：铃声库、应用过滤和记录；历史使用稳定 ID 并随持久化变化刷新。
- `update/UpdateChecker.kt` / `update/UpdateViewModel.kt`：GitHub 更新检查、下载和系统安装引导；数字版本段完整校验，支持 v 前缀与后缀。下载核对长度、资产大小、GitHub SHA-256（提供时）、包名、版本和当前签名；失败显示提示并清理临时文件。调试构建可使用本地更新接口，Release 使用 GitHub。

视觉设计、主题色值、响应式布局断点及图标资产规范见 [DESIGN.md](../DESIGN.md)。后台弹窗与锁屏行为受 Android 和厂商策略限制，不承诺自动弹出全屏通知。
