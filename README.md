<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" alt="Vigil Logo" width="120" />

# Vigil

**Android 通知关键词监控应用**

强制穿透静音 / 勿扰模式，让关键通知永远不会被错过

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/version-1.6.1-E4FF54)](https://github.com/XGWNJE/Vigil/releases)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[功能特性](#-功能特性) · [截图](#-截图) · [快速开始](#-快速开始) · [权限说明](#-权限说明) · [技术架构](#-技术架构)

</div>

---

## 简介

Vigil 是一款运行于 Android 的通知监控工具。当任意应用推送的通知内容命中预设关键词时，Vigil 会**在应用内弹出全屏报警提醒，并强制触发报警铃声**（后台运行时铃声同样生效）——即使设备处于静音或勿扰模式下也不例外。

适用场景：服务器宕机告警、银行到账提醒、特定消息监控等对通知实时性要求极高的场景。

---

## ✨ 功能特性

| 功能 | 描述 |
|------|------|
| **关键词匹配** | 多关键词 Chip 标签式管理，添加/删除即保存，无需手动确认 |
| **强制报警** | 绕过系统静音和勿扰模式，以 `STREAM_ALARM` 循环播放铃声；应用内全屏弹窗展示命中关键词与通知摘要 |
| **应用过滤** | 可选择只监听指定应用的通知，或监听全部应用 |
| **报警恢复** | 触发报警时持久化未确认状态，进程被系统杀死后重建可自动恢复响铃与弹窗 |
| **前台服务保活** | 以前台服务形式常驻，配合电池白名单降低系统回收概率 |
| **权限引导** | 主界面集中展示必要权限状态（通知使用权、电池白名单、自启动、后台运行），缺失时一键跳转授权 |
| **心跳检测** | 单调时钟心跳，实时感知服务存活状态；绑定断开时显示「重连中」而非误报「监听中」 |
| **监听自愈** | 服务看门狗检测到断连自动 `requestRebind`，失败时升级组件 toggle 重绑 |

---

## 📸 截图

<div align="center">

| 主界面 | 报警界面 | 应用过滤 |
|:--------:|:--------:|:--------:|
| <img src="./image1.png" alt="主界面" width="240" /> | <img src="./image2.png" alt="报警界面" width="240" /> | <img src="./image3.png" alt="应用过滤" width="240" /> |

*点击图片可查看大图*

</div>

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

# 构建 debug APK
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

### 安装

```bash
# 通过 ADB 安装到已连接设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 主力机若已安装 release 签名包，debug 包会因签名冲突无法覆盖安装，此时请改用 `./gradlew assembleRelease` 并安装 `app/build/outputs/apk/release/app-release.apk`（数据无损）。

### 首次使用

1. 打开应用，在首页完成权限授权：
   - **通知使用权**（核心必需，系统设置中授予）
   - **电池白名单**（防止省电策略强杀进程）
   - **自启动管理 / 后台运行**（国产 ROM 建议配置）
2. 点击关键词行的 **+** 添加需要监控的关键词
3. 点击「铃声」选择报警铃声（默认使用系统闹钟铃声）
4. 可选：点击「应用过滤」选择只监听特定应用
5. 打开顶部服务开关，状态显示「监听中」即开始工作

---

## 🔒 权限说明

| 权限 | 用途 | 授权方式 |
|------|------|----------|
| 通知使用权（NotificationListenerService） | 读取所有应用通知内容，核心功能依赖 | 系统设置手动授予 |
| 发送通知（POST_NOTIFICATIONS，Android 13+） | 前台服务常驻通知 | 首次启动自动弹窗 |
| 前台服务（FOREGROUND_SERVICE_SPECIAL_USE） | 维持后台监听服务持续运行 | 自动（Manifest 声明） |
| 唤醒锁（WAKE_LOCK） | 报警触发时保持 CPU 唤醒，确保铃声持续播放 | 自动（Manifest 声明） |
| 查询已安装应用（QUERY_ALL_PACKAGES） | 应用过滤列表枚举设备应用 | 自动（Manifest 声明） |
| 忽略电池优化（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS） | 防止厂商省电策略在报警响铃时强杀进程 | 首页「电池白名单」一键引导 |

> **注意**：通知使用权需在系统设置中手动授予，应用内提供直达跳转入口。

> **国产 ROM（小米 / 华为 / OPPO / vivo / 荣耀等）使用须知**：这些系统有激进的省电与自启动管控，可能在进程被清理后不再重新绑定监听服务。请在首页完成「自启动管理」与「后台运行」两项额外引导。应用内置断连自愈机制（心跳看门狗自动 `requestRebind` / 组件重绑），断连时状态显示「重连中」而非误报「监听中」。

---

## 🏗 技术架构

### 语言 / 框架

- **Kotlin** + **Jetpack Compose**（Material 3）
- **MVVM** 架构：`ViewModel` + `mutableStateOf`
- **Navigation Compose** 单页 + 子页导航

### 核心组件

| 文件 | 职责 |
|------|------|
| `MyNotificationListenerService` | 通知监听、关键词匹配、铃声播放、WakeLock 管理、报警恢复 |
| `VigilEventBus` | 基于 `SharedFlow` 的进程内事件总线 |
| `MonitoringViewModel` | 服务状态、心跳检测、报警 Dialog 状态管理 |
| `SettingsViewModel` | 关键词列表、铃声、应用过滤列表的状态与持久化 |
| `SharedPreferencesHelper` | 所有配置的读写封装（关键词以 `StringSet` 存储） |
| `ListenerRecovery` | 监听绑定自愈：`requestRebind` / 组件 toggle 重绑 |
| `PermissionUtils` | 各权限的检测与跳转逻辑，含国产 ROM 自启动 / 后台运行引导 |
| `MainActivity` | Compose 根宿主，生命周期管理，服务启停，报警弹窗承载 |

### UI 页面

| 页面 | 描述 |
|------|------|
| **MainScreen** | 「一线」单页主界面：状态词 + 服务大开关，发丝线列表承载关键词、铃声、应用过滤、权限入口 |
| **AppFilterScreen** | 「一线」独立全屏页，支持搜索、系统 / 用户应用标记、多选 |
| **KeywordAlertDialog** | 命中关键词时全屏弹出，确认后停止铃声；内容居中，避开系统导航栏 |
| **PermissionGuideDialog** | 「一线」权限引导确认弹窗，确认后跳转对应系统设置 |

### 设计主题

「一线」极简深色主题，发丝线分区、无卡片，单一强调色：

```
背景色:   #0A0A0B
文字色:   #EAEAE7
分割线:   #1F1F23
主色调:   #E4FF54（酸橙绿）
警示色:   #FFB020（琥珀）
```

---

## 📋 更新日志

<details open>
<summary><b>v1.6.1 — 图标与弹窗细节优化</b></summary>

**UI**
- 应用图标重设计：黑底酸橙绿铃铛气泡，保留原图标结构，适配系统圆角 mask（内容 75% 缩放 + 左移 20px，避免被裁切）
- 报警弹窗去掉外层灰色边框，仅保留酸橙绿单线框
- 报警弹窗内容整体居中，避开系统导航栏/手势条，防止底部按钮被挤出屏幕

**工程**
- 更新 `mipmap-*` 全部 launcher / round / foreground 图标资源
- 更新 `ic_launcher_background.xml` 为纯黑 `#0A0A0B`

</details>

<details>
<summary><b>v1.6.0 — 「一线」UI 全量统一</b></summary>

**UI**
- 应用过滤页整页重写为「一线」风格：暗底发丝线、主屏同款迷你胶囊开关、等宽统计信息、自定义勾选框（酸橙 ✓）、系统应用 SYS 标签与包名等宽副行；返回箭头触控区扩大至 48dp
- 权限引导弹窗（通知使用权 / 电池白名单 / 后台运行）由系统默认样式统一为「一线」风格 Compose 弹窗
- 修复 Material3 AlertDialog 默认 `tonalElevation` 将强调色叠加到容器底色、默认 28dp 圆角与边框错位成双框的问题
- 主屏与子页面顶部留白增加，状态栏与内容之间不再局促

**内部**
- 权限引导拆分为「Compose 弹窗说明 + PermissionUtils 纯跳转」，清理无用回调与死代码

</details>

<details>
<summary><b>v1.5.0 — 监听断连自愈与国产 ROM 保活</b></summary>

**修复**
- 修复"UI 显示监听中但实际收不到通知"的问题（小米 HyperOS 等国产 ROM 高发）：系统清理进程后可能不再重新绑定监听服务，但权限设置仍在，旧版仅靠心跳判断状态导致误报
- UI 状态改为以真实系统绑定状态为准（`onListenerConnected` 回调 + 持久化兜底），绑定断开时显示「重连中」而非误报「监听中」

**新增**
- 监听断连自动自愈：服务心跳看门狗检测到断连自动 `requestRebind`，连续失败升级为组件重绑
- 设置页新增「自启动管理」「后台运行」引导入口，降低系统清理进程导致的断连概率

</details>

<details>
<summary><b>v1.4.0 — 「一线」UI 重设计</b></summary>

**UI 全面重设计**
- 监控 / 设置双 Tab 合并为单页：状态词 + 服务大开关为核心，发丝线分区、去卡片化
- 报警弹窗重设计：酸橙绿细框 + 超大关键词 + 等宽元信息（来源 / 内容 / 时间）
- 新增「一线」配色规范：背景 #0A0A0B，主色调酸橙绿 #E4FF54
- 设计稿存档于 `design/v2-proposals/`（含 4 个候选方向，当前落地为 A 方向）

</details>

<details>
<summary><b>v1.3.2 — Bug 修复与报警可靠性增强</b></summary>

**修复**
- 修复通知含关键词时报警不触发、手动下拉通知栏后才触发的竞态条件 Bug
- 为 `keywords.isEmpty()` 的静默返回路径添加日志，便于后续诊断

**新增**
- 电池优化白名单引导，修复摩托罗拉 Device Guard 等厂商省电策略强杀进程问题
- 报警状态持久化与进程重启恢复：触发报警时持久化未确认状态，进程被杀重建后自动恢复响铃与弹窗

</details>

<details>
<summary><b>v1.3.1 — 权限与交互精简</b></summary>

**权限调整**
- 移除悬浮窗权限（`SYSTEM_ALERT_WINDOW`）：报警弹窗在应用内展示
- `POST_NOTIFICATIONS` 改为首次启动及权限被撤销后自动弹出系统授权对话框
- 设置页权限列表精简至唯一核心权限：通知使用权

**UI / UX**
- 通知使用权旁新增可折叠说明
- 底部导航"权限设置"标签改为"设置"
- 移除首次启动监控服务时弹出的开发者支持对话框

</details>

<details>
<summary><b>v1.3 — 全面重构</b></summary>

**服务稳定性**
- 关键词和应用过滤包名变量加 `@Volatile`
- `MediaPlayer` 引入状态枚举，防止并发播放导致资源泄漏
- 服务重连改用 `NotificationListenerService.requestRebind()` 官方 API
- 心跳时钟改用 `SystemClock.elapsedRealtime()`
- Service 添加 `CoroutineScope(SupervisorJob())`
- WakeLock 超时从 2 分钟延长至 5 分钟
- `AndroidManifest.xml` 前台服务类型改为 `specialUse`

**事件总线**
- 新建 `VigilEventBus`（`MutableSharedFlow` 单例），替换废弃的 `LocalBroadcastManager`

**数据修复**
- 关键词存储改为 `SharedPreferences.StringSet`，修复含逗号关键词被错误拆分的 Bug
- 新增 `migrateKeywordsIfNeeded()` 一次性迁移旧格式数据

**UI / UX**
- 全新深色主题
- 应用过滤改为独立全屏页

</details>

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

<div align="center">

Made with ❤️ for Android

</div>
