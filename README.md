# Vigil

一款 Android 通知关键词监控应用。当任意应用推送的通知内容命中预设关键词时，Vigil 会强制触发报警铃声并弹出提醒界面——即使设备处于静音或勿扰模式。

---

## 功能

- **关键词匹配** — 支持多关键词，逐条添加/删除，实时保存，无需手动点击"保存"按钮
- **强制报警** — 绕过系统静音和勿扰模式播放自定义铃声，唤醒屏幕并弹出报警对话框
- **应用过滤** — 可选择只监听指定应用的通知，或监听全部应用
- **前台服务保活** — 以前台服务形式运行，常驻状态栏通知，降低系统回收概率
- **权限引导** — 设置页集中展示所有必要权限状态，缺失时一键跳转授权

---

## 权限说明

| 权限 | 用途 |
|------|------|
| 通知使用权（NotificationListenerService） | 读取所有应用通知内容，核心功能依赖 |
| 勿扰模式控制（ACCESS_NOTIFICATION_POLICY） | 报警时临时突破勿扰模式播放铃声 |
| 悬浮窗（SYSTEM_ALERT_WINDOW） | 在锁屏/其他应用之上弹出报警界面 |
| 发送通知（POST_NOTIFICATIONS，Android 13+） | 显示前台服务常驻通知 |
| 前台服务（FOREGROUND_SERVICE_SPECIAL_USE） | 维持后台监听服务持续运行 |
| 唤醒锁（WAKE_LOCK） | 报警触发时点亮屏幕 |
| 查询已安装应用（QUERY_ALL_PACKAGES） | 应用过滤列表枚举设备应用 |

> 通知使用权和悬浮窗权限需在系统设置中手动授予，应用内提供跳转入口。

---

## 技术架构

**语言 / 框架**

- Kotlin + Jetpack Compose（Material 3）
- MVVM 架构，`ViewModel` + `StateFlow` / `mutableStateOf`
- Navigation Compose 多屏导航

**核心组件**

| 文件 | 职责 |
|------|------|
| `MyNotificationListenerService` | 通知监听、关键词匹配、铃声播放、WakeLock |
| `VigilEventBus` | 基于 `SharedFlow` 的进程内事件总线（替代废弃的 LocalBroadcastManager） |
| `MonitoringViewModel` | 服务状态、心跳检测、报警 Dialog 状态管理 |
| `SettingsViewModel` | 关键词列表、铃声、应用过滤列表的状态与持久化 |
| `SharedPreferencesHelper` | 所有配置的读写封装（关键词以 `StringSet` 存储） |
| `PermissionUtils` | 各权限的检测与跳转逻辑，含 MIUI 兼容分支 |
| `MainActivity` | Compose 根宿主，生命周期管理，服务启停 |

**UI 页面**

- **监控页（MonitoringScreen）** — 服务开关、运行状态、心跳指示
- **设置页（SettingsScreen）** — 权限状态、关键词 Chip 输入、铃声选择、应用过滤入口
- **应用过滤页（AppFilterScreen）** — 独立全屏页，支持搜索、系统/用户应用标记、复选
- **报警对话框（KeywordAlertDialog）** — 命中关键词时全屏弹出，支持确认停止铃声

---

## 环境要求

- **最低 Android 版本**：8.0（API 26）
- **目标 SDK**：35（Android 15）
- **编译 SDK**：35

---

## 项目仓库

[https://github.com/XGWNJE/Vigil](https://github.com/XGWNJE/Vigil)

---

## 本次重构说明

相较初始版本，本次对服务稳定性、数据正确性和 UI 体验做了全面重写：

**服务稳定性**
- 关键词和应用过滤包名变量加 `@Volatile`，消除多线程缓存不可见问题
- `MediaPlayer` 引入状态枚举（`IDLE / PREPARING / PLAYING / STOPPED`），防止并发播放导致资源泄漏或 `IllegalStateException`
- 服务重连改用 `NotificationListenerService.requestRebind()` 官方 API，替换原有 `Handler.postDelayed` 竞态方案
- 心跳时钟改用 `SystemClock.elapsedRealtime()`（单调时钟），不受 NTP 或时区变更影响，消除误报"服务异常"
- Service 添加 `CoroutineScope(SupervisorJob())`，生命周期结束时统一取消所有协程
- WakeLock 超时从 2 分钟延长至 5 分钟，确认报警后主动提前释放
- `AndroidManifest.xml` 前台服务类型从 `dataSync` 改为 `specialUse`，符合 Android 14+ 要求

**事件总线**
- 新建 `VigilEventBus`（`MutableSharedFlow` 单例），完整替换废弃的 `LocalBroadcastManager`，覆盖心跳、服务状态、报警触发、报警确认四类事件

**数据修复**
- 关键词存储从逗号拼接字符串改为 `SharedPreferences.StringSet`，彻底修复含逗号关键词被错误拆分的 Bug
- 新增 `migrateKeywordsIfNeeded()` 一次性迁移旧格式数据，升级无感知

**UI / UX**
- 关键词输入改为 Chip 标签式，支持独立添加和删除，操作即保存
- 应用过滤从 Settings 页内嵌 400dp 嵌套 LazyColumn，改为独立全屏 `AppFilterScreen`，消除嵌套滚动冲突
- Settings 页删除捐赠卡片，权限区精简为紧凑状态行：已授权时仅显示小绿点，未授权时才展示"前往设置"按钮
- 应用列表隐藏包名，仅展示应用名和系统/用户标记
- 删除冗余的 MIUI "后台弹窗权限"条目（与悬浮窗权限实为同一权限）
