# 隐私政策 / Privacy Policy

**生效日期 / Effective Date: 2026-07-29**

---

## 中文

Vigil（以下简称"本应用"）是一款开源的关键词通知报警工具。我们高度重视你的隐私。本隐私政策说明本应用如何处理你的信息。

### 核心结论

**本应用的一切数据处理均在你的设备本地完成，不会将任何信息传输到任何服务器。**

### 本应用收集的信息

本应用**不收集、不存储（在设备之外）、不传输**任何个人信息。具体来说：

- **通知内容**：本应用通过 Android 通知监听服务读取系统通知，仅用于在你的设备本地进行关键词匹配。通知内容不会被上传到任何服务器，也不会写入诊断日志。
- **关键词与设置**：你设置的关键词、铃声选择、应用过滤等配置，仅保存在你设备的应用私有存储（SharedPreferences）中。
- **诊断日志**：日志文件保存在应用私有目录，仅供你自行导出用于故障排查，不包含通知正文，应用不会自动发送。

### 本应用不做的的事

- 不注册账号，无需登录
- 不集成任何统计、分析或广告 SDK
- 不进行任何网络请求（应用未声明 INTERNET 权限，不具备联网能力，所有功能离线运行）
- 不读取通讯录、位置、相册等无关数据

### 权限说明

| 权限 | 用途 |
|---|---|
| 通知监听（Notification Listener） | 核心功能：读取系统通知以匹配关键词，仅本地处理 |
| 前台服务（含 specialUse） | 报警时保持服务存活，循环播放铃声 |
| 唤醒锁（WAKE_LOCK） | 报警期间保持设备唤醒，确保你能看到报警 |
| 全屏通知（USE_FULL_SCREEN_INTENT） | 报警时展示全屏提醒 |
| 请求忽略电池优化 | 防止系统省电策略强杀报警服务，由你自愿开启 |

### 数据安全

由于所有数据均仅存于你的设备本地，其安全性取决于你设备本身的安全措施（如锁屏密码）。卸载应用会清除全部配置与日志。

### 开源透明

本应用全部源代码公开，你可以随时审查我们的实际行为：https://github.com/XGWNJE/Vigil

### 政策变更

如本政策发生变更，将在本页面更新并注明生效日期。

### 联系我们

如有隐私相关问题，请通过 GitHub Issues 反馈：https://github.com/XGWNJE/Vigil/issues

---

## English

Vigil ("the App") is an open-source keyword notification alarm tool. We take your privacy seriously. This policy explains how the App handles your information.

### TL;DR

**All data processing happens entirely on your device. Nothing is ever transmitted to any server.**

### Information the App Collects

The App does **not** collect, store (outside your device), or transmit any personal information. Specifically:

- **Notification content**: The App reads system notifications via Android's Notification Listener Service, solely for on-device keyword matching. Notification content is never uploaded to any server and is never written to diagnostic logs.
- **Keywords & settings**: Your keywords, ringtone choices, app filters and other configurations are stored only in the App's private storage (SharedPreferences) on your device.
- **Diagnostic logs**: Log files stay in the App's private directory. They can only be exported manually by you for troubleshooting, contain no notification content, and are never sent automatically.

### What the App Does NOT Do

- No account registration, no sign-in
- No analytics, tracking, or advertising SDKs
- No network requests of any kind — the App does not even declare the INTERNET permission and is technically incapable of going online; all features work fully offline
- No access to contacts, location, photos, or any unrelated data

### Permissions Explained

| Permission | Purpose |
|---|---|
| Notification Listener | Core feature: reads system notifications for keyword matching, processed locally only |
| Foreground Service (specialUse) | Keeps the alarm service alive to loop the alarm sound |
| WAKE_LOCK | Keeps the device awake during an alarm so you can see it |
| USE_FULL_SCREEN_INTENT | Shows the full-screen alarm alert |
| Request ignore battery optimizations | Prevents the system from killing the alarm service; enabled voluntarily by you |

### Data Security

Since all data resides only on your device, its security depends on your device's own protections (e.g., screen lock). Uninstalling the App removes all configurations and logs.

### Open Source Transparency

The App's complete source code is publicly available for audit: https://github.com/XGWNJE/Vigil

### Changes to This Policy

Any changes will be posted on this page with an updated effective date.

### Contact Us

For privacy-related questions, please open an issue: https://github.com/XGWNJE/Vigil/issues
