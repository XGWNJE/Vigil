# Vigil 商店文案素材

供 Google Play 与国内各应用商店填表使用。商店截图用 `screenshots/` 目录（`main.png` / `alert.png` / `app-filter.png`）；UI 设计稿存档见 `design-backup/`，图标素材见 `design/icons/`。

---

## 一、基本信息

- 应用名称：Vigil
- 包名：com.example.vigil
- 分类建议：工具（Tools）/ 效率（Productivity）
- 内容分级：适合所有人（无暴力、无赌博、无社交功能、无广告）
- 官网/源代码：https://github.com/XGWNJE/Vigil
- 隐私政策：https://github.com/XGWNJE/Vigil/blob/main/PRIVACY.md

---

## 二、短描述（≤80 字符）

### 中文（46 字符）
```
轻量关键词通知报警：监控指定应用通知，命中关键词即循环响铃+应用内全屏弹窗，绝不错过重要消息
```

### English (70 chars)
```
Keyword notification alarm: looping ringtone + in-app alert, 2.5MB APK
```

---

## 三、完整描述

### 中文

```
Vigil — 关键词通知报警器

重要消息淹没在通知海洋里？Vigil 帮你盯住它们。

设置关键词后，Vigil 会在后台监控指定应用的通知。一旦通知内容命中关键词，立即触发强提醒：按设定次数播放闹钟铃声 + 应用内全屏弹窗，并在响铃期间显示可点击进入处理的前台通知。你可以手动确认停止；无人处理时到达设定次数会自动结束并留下报警记录。进程重建后，仍在有效期内的未确认报警会恢复提醒。

【核心功能】
• 关键词监控：自定义多个关键词，命中即报警
• 应用过滤：只监控你关心的应用，避免误报
• 强提醒：循环响铃 + 应用内全屏弹窗，想忽略都难
• 极致轻量：安装包仅 2.5MB，几乎不占空间
• 报警恢复：即使进程被系统强杀，重启后自动恢复响铃，报警绝不丢失
• 自定义铃声：使用系统闹钟铃声，音量独立控制
• 诊断日志：可选导出，便于排查问题（不含通知内容）

【适用场景】
• 值班运维：监控告警群消息，半夜不再漏接
• 宝妈宝爸：监控学校通知群的关键词
• 交易提醒：盯盘软件的特定信号通知
• 任何"这条消息我必须第一时间看到"的时刻

【隐私承诺】
全部数据仅在你的设备本地处理。应用甚至没有声明联网权限——它根本无法联网。不收集任何信息，无广告，无统计 SDK。完整源代码公开可审。

【权限说明】
• 通知监听：核心功能，读取通知用于本地关键词匹配
• 前台服务/唤醒锁：报警期间保持服务运行，确保循环响铃不被中断
• 忽略电池优化（可选）：防止系统省电策略中断报警
• 麦克风（可选）：仅用于铃声库录制自定义铃声，录音仅存本地

开源地址：https://github.com/XGWNJE/Vigil
```

### English

```
Vigil — Keyword Notification Alarm

Drowning in notifications? Vigil makes sure you never miss the ones that matter.

Set your keywords, and Vigil monitors notifications from the apps you choose. When a notification matches a keyword, it fires a hard-to-ignore alarm: looping ringtone + in-app full-screen alert that keeps going until you acknowledge it. Missed it anyway? Unacknowledged alarms pop up again the next time you open the app.

[Features]
• Keyword monitoring: define multiple keywords, alarm on match
• App filter: watch only the apps you care about
• Hard-to-miss alarm: looping sound + in-app full-screen alert
• Ultra-lightweight: only a 2.5MB APK, negligible storage footprint
• Alarm recovery: even if the process is killed, the alarm resumes automatically after restart
• Custom ringtone: uses system alarm sounds with independent volume
• Diagnostic logs: optional export for troubleshooting (never contains notification content)

[Use cases]
• On-call engineers: catch alerts buried in chat groups at 3 AM
• Parents: monitor school group messages for key announcements
• Traders: catch specific signals from market apps
• Any "I MUST see this message immediately" moment

[Privacy]
All processing happens entirely on your device. The app doesn't even declare the INTERNET permission — it is technically incapable of going online. No data collection, no ads, no analytics SDKs. Fully open source and auditable.

[Permissions]
• Notification Listener: core feature, reads notifications for local keyword matching
• Foreground service / wake lock: keeps the alarm service alive and ringing until you acknowledge it
• Ignore battery optimizations (optional): prevents the system from killing the alarm

Source code: https://github.com/XGWNJE/Vigil
```

---

## 四、国内商店权限用途说明表（草稿）

国内商店（小米/华为/OPPO/vivo/应用宝等）通常要求逐项说明权限用途，可直接复制：

| 权限 | 用途说明 |
|---|---|
| 通知使用权（通知监听） | 核心功能。读取系统通知内容，仅在设备本地与用户设置的关键词比对，命中时触发报警。不存储、不上传通知内容。 |
| 前台服务 | 报警触发时保持服务运行，持续播放报警铃声直至用户确认。 |
| 特殊用途前台服务（specialUse） | 同上，用于 Android 14+ 系统声明通知监听类前台服务的合法用途。 |
| 唤醒锁（WAKE_LOCK） | 报警期间保持 CPU 唤醒运行，确保铃声持续播放。 |
| 全屏通知（USE_FULL_SCREEN_INTENT） | 后台无法弹出应用内弹窗时，以全屏通知形式在锁屏提醒用户。 |
| 通知权限（POST_NOTIFICATIONS） | Android 13+ 显示前台服务状态通知与报警提醒。 |
| 忽略电池优化（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS） | 用户自愿开启。防止系统省电策略（如厂商管家类应用）强杀报警服务导致报警失效。 |
| 麦克风（RECORD_AUDIO） | 仅用于铃声库「录音」功能：用户主动录制自定义报警铃声，录音文件仅保存在应用私有目录本地使用，不上传。拒绝授权不影响其他功能。 |

补充说明（若商店审核问询）：
- 应用未声明 INTERNET 权限，不具备联网能力，所有数据处理均在设备本地完成。
- 应用不申请读取应用列表权限（QUERY_ALL_PACKAGES），"应用过滤"功能仅列出桌面可见应用（通过 launcher intent 查询，无需任何权限）。
- 应用需前台服务常驻以稳定监听通知，建议用户长期保持其在后台运行；多数安卓系统对后台权限与自启动的管理都比较严格，若用户仅临时使用、用完即退出，之后系统会更频繁地要求重新授权，属正常现象。

---

## 五、Google Play 专属表单备忘

- **Data safety（数据安全）**：全部选 "No data collected / No data shared"。
- **Notification Listener 权限声明**：Play Console 会要求填写通知监听权限的用途说明，可用：
  > Vigil 的核心功能是关键词通知报警。用户主动设置关键词后，应用需监听系统通知并在本地进行关键词匹配，命中时触发循环响铃与全屏报警。通知内容仅在设备本地处理，不存储、不传输。此为本应用的唯一核心用途，无替代实现方式。
- **FOREGROUND_SERVICE_SPECIAL_USE 声明**（Android 14+ 要求）：
  > The app is a keyword-based notification alarm. When a watched notification matches a user-defined keyword, the app must keep running a foreground service to loop an alarm sound and keep the screen awake until the user acknowledges it. This cannot be deferred or interrupted without breaking the app's core function.
- **广告**：无广告。
- **目标受众**：非儿童向应用（建议选 18+ 或 "Not designed for children"，避免家庭政策审核）。
