# Vigil 商店上架素材

本文件供维护者填写应用商店资料。功能事实以 [README](../README.md) 为准；数据处理与网络访问以 [隐私政策](../PRIVACY.md) 为准。上架前需按待提交 APK 和商店表单再次核对。

当前源码尚未发布；商店资料须以待提交 APK 为准。[screenshots](../screenshots/) 的 `main.png` 为当前平板接警页，其他既有截图须在提交前用当前 APK 重新验收采集；Feature Graphic 为 [feature-graphic.png](feature-graphic.png)。

## 一、基本信息

| 项目 | 内容 |
| --- | --- |
| 应用名称 | Vigil |
| 包名 | `com.example.vigil` |
| 源代码 | https://github.com/XGWNJE/Vigil |
| 隐私政策 | https://github.com/XGWNJE/Vigil/blob/main/PRIVACY.md |
| 当前版本 | `v1.19.0`，以待提交 APK 的版本为准 |

## 二、短描述

**中文**

> 监控 Android 通知关键词，命中后按设置响铃，并在应用内显示报警。

**English**

> Get ringtone and in-app alerts when Android notifications match your keywords.

## 三、完整描述

### 中文

> Vigil 是一款 Android 通知与接警应用，可选择连接已登记的 VisionGuard 服务接收实时事件，也可独立监听本机通知。添加关键词并授予通知使用权后，它会在设备上匹配通知；命中时播放闹钟铃声，并在应用内显示报警弹窗。你可以确认停铃，也可以设置播放 1–10 次后自动结束。
>
> 你可以指定要监听的应用，给不同关键词选择铃声和循环次数，并设置重复提醒间隔。需要稍后处理时，可把报警设为固定延时或每日指定时刻；待触发项可在设置中查看或取消。多条提醒会按顺序处理，结束后可查看报警记录。
>
> 通知匹配在设备本地进行，未结束的报警保存通知摘要用于恢复弹窗。应用启动时会访问 GitHub 检查更新；你选择下载时才会获取更新 APK。诊断日志不含通知正文，可由你主动导出；通知节点连接会发送身份、心跳和收件确认，不上传本机通知正文；Android 系统备份或迁移可能包含其他私有数据，节点凭据明确排除，详见隐私政策。
>
> 使用前请授予通知使用权，并按设备情况检查电池优化和后台运行设置。系统勿扰规则、闹钟音量及厂商省电策略可能影响提醒；Android 12 及以上未授予「闹钟和提醒」时，延时报警可能晚于设定时间。

### English

> Vigil is an Android notification and alarm app with optional registered VisionGuard service alerts. After you add keywords and grant Notification Listener access, it matches notifications on your device. A match plays an alarm ringtone and shows an in-app alert. Acknowledge it to stop, or let it finish after 1–10 plays.
>
> Choose which apps to watch, assign ringtones and repeat counts per keyword, and set an interval for repeated alerts. You can delay an alert by a fixed duration or until the next selected daily time. Review or cancel pending alerts in Settings. Alerts from different keywords are queued, and completed alerts appear in history.
>
> Notifications are matched locally; pending alarms retain snippets to restore their dialogs. The app checks GitHub for updates on startup and downloads an APK only if you choose to update. Diagnostic logs exclude notification bodies and can be exported. The optional node sends identity, heartbeats and receipts, without uploading local notification bodies. Backup or transfers may include other private data; node credentials are excluded, as described in the privacy policy.
>
> Grant Notification Listener access before use and check your device's battery and background restrictions. Do Not Disturb rules, alarm volume, and device power management can affect alerts. Without Alarms & reminders access on Android 12+, delayed alerts may arrive late.

## 四、权限用途说明表

| 权限或系统授权 | 用途 |
| --- | --- |
| 通知使用权 | 在设备上读取通知并匹配用户设置的关键词 |
| 发送通知（Android 13+） | 显示监听状态通知和报警通知 |
| 前台服务（含 specialUse） | 后台维持通知监听和报警服务 |
| 唤醒锁 | 报警播放期间保持 CPU 运行 |
| 忽略电池优化 | 降低省电策略中断服务的概率，由用户在系统设置中选择 |
| 闹钟和提醒（Android 12+） | 到期触发延时报警；未授权时回落非精确闹钟 |
| 麦克风 | 用户主动录制自定义铃声时使用 |
| 联网 | 可选通知节点连接配置的服务、心跳与收件确认；GitHub 更新查询与下载 |
| 安装未知应用 | 用户选择安装下载的 APK 时，由系统引导授权 |

应用过滤只查询设备上可见的桌面应用，未申请 `QUERY_ALL_PACKAGES` 或 `USE_FULL_SCREEN_INTENT`。报警通知没有全屏 Intent；商店资料不要描述“锁屏自动弹出全屏通知”。

## 五、上架表单备忘

- 以 [隐私政策](../PRIVACY.md) 和待提交 APK 为依据填写数据安全问卷；更新检查会访问 GitHub，不得填写“应用没有联网权限”。
- 通知使用权用途：用户设置关键词后，应用在设备本地读取并匹配系统通知；命中时播放铃声并显示应用内报警，通知正文不作为更新请求发送。
- 特殊用途前台服务用途：持续监听本机通知或维持已启用的服务连接，并在报警期间维持铃声播放，直到用户确认或达到设定次数。
- 「闹钟和提醒」用途：用户配置固定延时或每日定点报警时安排到期触发；未授权时使用非精确闹钟。
- 商店表单、分类及审核要求可能变化；提交前以对应商店当前页面为准。[owner-checklist.md](owner-checklist.md) 记录维护者待办。
