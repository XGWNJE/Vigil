<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" alt="Vigil Logo" width="120" />

# Vigil

**Android 通知关键词监控应用**

强制穿透静音 / 勿扰模式，让关键通知永远不会被错过

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[功能特性](#-功能特性) · [截图](#-截图) · [快速开始](#-快速开始) · [权限说明](#-权限说明) · [技术架构](#-技术架构) · [贡献指南](#-贡献指南)

</div>

---

## 简介

Vigil 是一款运行于 Android 的通知监控工具。当任意应用推送的通知内容命中预设关键词时，Vigil 会**强制触发报警铃声并弹出提醒界面**——即使设备处于静音或勿扰模式下也不例外。

适用场景：服务器宕机告警、银行到账提醒、特定消息监控等对通知实时性要求极高的场景。

---

## ✨ 功能特性

| 功能 | 描述 |
|------|------|
| **关键词匹配** | 支持多关键词，Chip 标签式管理，操作即保存，无需手动确认 |
| **强制报警** | 绕过系统静音和勿扰模式播放铃声，唤醒屏幕并弹出全屏报警对话框 |
| **应用过滤** | 可选择只监听指定应用的通知，或监听全部应用 |
| **前台服务保活** | 以前台服务形式常驻，降低系统回收概率 |
| **权限引导** | 设置页集中展示所有必要权限状态，缺失时一键跳转授权 |
| **心跳检测** | 单调时钟心跳，实时感知服务存活状态，避免误报 |

---

## 📸 截图

> *截图待补充*

---

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17+
- 最低 Android 版本：**8.0（API 26）**
- 目标 / 编译 SDK：**35（Android 15）**

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/XGWNJE/Vigil.git
cd Vigil

# 使用 Gradle Wrapper 构建（无需本地安装 Gradle）
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

### 安装

```bash
# 通过 ADB 安装到已连接设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

或直接在 Android Studio 中点击 **Run** 部署到设备 / 模拟器。

### 首次使用

1. 打开应用，进入 **设置页** 完成权限授权（通知使用权、悬浮窗）
2. 在设置页添加需要监控的**关键词**
3. 可选：在**应用过滤**中选择只监听特定应用
4. 返回**监控页**，开启服务开关
5. 服务启动后，状态卡片将显示运行状态与心跳时间

---

## 🔒 权限说明

| 权限 | 用途 | 授权方式 |
|------|------|----------|
| 通知使用权（NotificationListenerService） | 读取所有应用通知内容，核心功能依赖 | 系统设置手动授予 |
| 勿扰模式控制（ACCESS_NOTIFICATION_POLICY） | 报警时临时突破勿扰模式播放铃声 | 系统弹窗 |
| 悬浮窗（SYSTEM_ALERT_WINDOW） | 在锁屏 / 其他应用之上弹出报警界面 | 系统设置手动授予 |
| 发送通知（POST_NOTIFICATIONS，Android 13+） | 显示前台服务常驻通知 | 运行时弹窗 |
| 前台服务（FOREGROUND_SERVICE_SPECIAL_USE） | 维持后台监听服务持续运行 | 自动（Manifest 声明） |
| 唤醒锁（WAKE_LOCK） | 报警触发时点亮屏幕 | 自动（Manifest 声明） |
| 查询已安装应用（QUERY_ALL_PACKAGES） | 应用过滤列表枚举设备应用 | 自动（Manifest 声明） |

> **注意**：通知使用权和悬浮窗权限需在系统设置中手动授予，应用内提供直达跳转入口。

---

## 🏗 技术架构

### 语言 / 框架

- **Kotlin** + **Jetpack Compose**（Material 3）
- **MVVM** 架构：`ViewModel` + `StateFlow` / `mutableStateOf`
- **Navigation Compose** 多屏导航

### 核心组件

| 文件 | 职责 |
|------|------|
| `MyNotificationListenerService` | 通知监听、关键词匹配、铃声播放、WakeLock 管理 |
| `VigilEventBus` | 基于 `SharedFlow` 的进程内事件总线（替代废弃的 LocalBroadcastManager） |
| `MonitoringViewModel` | 服务状态、心跳检测、报警 Dialog 状态管理 |
| `SettingsViewModel` | 关键词列表、铃声、应用过滤列表的状态与持久化 |
| `SharedPreferencesHelper` | 所有配置的读写封装（关键词以 `StringSet` 存储） |
| `PermissionUtils` | 各权限的检测与跳转逻辑，含 MIUI 兼容分支 |
| `MainActivity` | Compose 根宿主，生命周期管理，服务启停 |

### UI 页面

| 页面 | 描述 |
|------|------|
| **MonitoringScreen** | 服务开关、运行状态、心跳指示器 |
| **SettingsScreen** | 权限状态、关键词 Chip 输入、铃声选择、应用过滤入口 |
| **AppFilterScreen** | 独立全屏页，支持搜索、系统 / 用户应用标记、多选 |
| **KeywordAlertDialog** | 命中关键词时全屏弹出，确认后停止铃声 |

### 设计主题

深色主题为主，色彩规范如下：

```
背景色:   #0A0A0A
卡片色:   #141414
边框色:   #27272A
主色调:   #A855F7（紫色）
```

---

## 📋 更新日志

<details>
<summary><b>v1.3 — 全面重构</b></summary>

**服务稳定性**
- 关键词和应用过滤包名变量加 `@Volatile`，消除多线程缓存不可见问题
- `MediaPlayer` 引入状态枚举（`IDLE / PREPARING / PLAYING / STOPPED`），防止并发播放导致资源泄漏或 `IllegalStateException`
- 服务重连改用 `NotificationListenerService.requestRebind()` 官方 API
- 心跳时钟改用 `SystemClock.elapsedRealtime()`（单调时钟），消除 NTP / 时区变更导致的误报
- Service 添加 `CoroutineScope(SupervisorJob())`，生命周期结束时统一取消所有协程
- WakeLock 超时从 2 分钟延长至 5 分钟，确认报警后主动提前释放
- `AndroidManifest.xml` 前台服务类型从 `dataSync` 改为 `specialUse`，符合 Android 14+ 要求

**事件总线**
- 新建 `VigilEventBus`（`MutableSharedFlow` 单例），完整替换废弃的 `LocalBroadcastManager`

**数据修复**
- 关键词存储从逗号拼接字符串改为 `SharedPreferences.StringSet`，修复含逗号关键词被错误拆分的 Bug
- 新增 `migrateKeywordsIfNeeded()` 一次性迁移旧格式数据，升级无感知

**UI / UX**
- 全新深色主题，紫色主色调
- 监控页重新设计：状态卡片含图标 + 状态徽章 + 诊断信息展开面板
- 关键词输入改为 Chip 标签式，操作即保存
- 应用过滤改为独立全屏页，消除嵌套滚动冲突

</details>

---


## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

<div align="center">

Made with ❤️ for Android

</div>
