# 隐私政策 / Privacy Policy

生效日期 / Effective date: 2026-09-29

## 中文

Vigil 是一款开源的 Android 通知关键词报警应用。本政策说明它在设备本地处理哪些数据，以及更新功能何时访问网络。

### 本地数据

- **通知内容**：获得通知使用权后，Vigil 在设备上读取通知并与设置的关键词比对。通知正文不会上传，也不会写入诊断日志。
- **设置与报警**：关键词、应用过滤、铃声配置、待触发报警、报警队列和报警记录保存在应用私有存储中。报警记录包含关键词、来源应用、时间和结束方式；导入或录制的铃声文件也保存在设备上。
- **诊断日志**：日志保存在应用私有目录。只有你主动使用「导出日志」并选择分享目标时，日志才会离开应用。

### 网络访问

应用声明了 `INTERNET` 权限。启动时会向 GitHub 查询最新版本；你也可以在设置页手动检查更新。选择下载更新后，应用会从 GitHub 下载 APK，再交给 Android 系统安装器。应用不会把通知正文、关键词、报警记录或铃声文件作为更新请求内容发送。访问 GitHub 时，GitHub 可能按其政策处理连接信息，例如 IP 地址；详见 [GitHub 隐私声明](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement)。

Vigil 不要求注册账号，也没有集成广告或分析 SDK。报警与关键词匹配无需联网；无法连接 GitHub 时，更新检查或下载不能完成。

### 权限用途

| 权限或系统授权 | 用途 |
| --- | --- |
| 通知使用权 | 读取系统通知，在本机匹配关键词 |
| 发送通知、前台服务、唤醒锁 | 展示监听或报警通知，并在报警期间保持服务与铃声运行 |
| 忽略电池优化 | 减少系统省电策略中断监听的概率，由你在系统设置中选择 |
| 闹钟和提醒 | 安排延时报警；未授权时使用非精确闹钟 |
| 麦克风 | 仅在你选择录制自定义铃声时申请 |
| 安装未知应用 | 仅在你选择安装下载的更新包时由系统引导授权 |

卸载应用通常会删除其私有数据；设备备份和恢复行为由 Android 及设备设置决定。你可以在设置中清空报警记录，也可以通过系统设置清除应用数据。

如有隐私问题，请通过 [GitHub Issues](https://github.com/XGWNJE/Vigil/issues) 联系项目维护者。政策更新会在本页注明新的生效日期。

## English

Vigil is an open-source Android app that alarms on matching notification keywords. This policy explains what it handles on your device and when its update feature uses the network.

### On-device data

- **Notifications**: With Notification Listener access, Vigil reads notifications and matches them against your keywords on the device. Notification bodies are neither uploaded nor written to diagnostic logs.
- **Settings and alarms**: Keywords, app filters, ringtone choices, scheduled alerts, the alert queue, and alert history are stored in app-private storage. History includes the keyword, source app, time, and how the alert ended. Imported or recorded ringtones stay on the device.
- **Diagnostic logs**: Logs remain in app-private storage unless you explicitly choose Export logs and a sharing destination.

### Network access

The app declares the `INTERNET` permission. It checks GitHub for a newer release on startup and when you request a check in Settings. If you choose to download an update, it downloads the APK from GitHub and hands it to the Android installer. Vigil does not include notification bodies, keywords, alert history, or ringtone files in update requests. GitHub may process connection data such as your IP address under its [privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

Vigil requires no account and includes no advertising or analytics SDK. Keyword matching and alarms work without network access; update checks and downloads require access to GitHub.

### Permissions

| Permission or system access | Purpose |
| --- | --- |
| Notification Listener | Read system notifications for on-device keyword matching |
| Notifications, foreground service, wake lock | Show status or alert notifications and keep the service and ringtone running during an alarm |
| Ignore battery optimizations | Reduce interruptions by device power management, at your choice |
| Alarms & reminders | Schedule delayed alerts; fall back to inexact alarms if unavailable |
| Microphone | Requested only when you record a custom ringtone |
| Install unknown apps | Requested by the system only if you choose to install a downloaded update |

Uninstalling normally removes app-private data; Android and device backup settings determine whether data can later be restored. You can clear alert history in the app or clear app data through system settings.

For privacy questions, use [GitHub Issues](https://github.com/XGWNJE/Vigil/issues). Changes to this policy will be dated on this page.
