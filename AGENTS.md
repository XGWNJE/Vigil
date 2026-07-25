# Vigil 项目规则

Android 关键词通知报警应用（Kotlin + Jetpack Compose，MVVM）。
核心链路：通知命中关键词 → 循环播放闹钟铃声 + 唤醒锁 + 应用内全屏弹窗 → 用户确认后停止。

## 铁律（不可违反）

1. 真机测试不得让设备实际出声：先调最小音量，一律用 `dumpsys media.player` 验证播放/停止，不依赖人耳。
2. 改动报警核心链路（通知匹配 → 响铃 → 弹窗 → 确认停止）后，必须在真机上跑完整闭环测试才允许交付；不接受纯静态检查结论。
3. 报警状态必须持久化（SharedPreferences），不得只存内存：厂商省电策略（Motorola Device Guard 等）随时可能强杀进程，内存状态 = 报警丢失。
4. 不得删除或替换 `keystore/vigil.keystore`：release 签名依赖它，丢失则无法发布更新包。
5. `VigilEventBus` 是无 replay 的 SharedFlow，进程重建后事件即丢；任何"服务 → UI"的关键事件都必须有持久化兜底。

## 关键路径与命令

- 构建：`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- 版本号：`app/build.gradle.kts` 的 `versionCode`/`versionName`
- 核心代码：
  - `app/src/main/java/com/example/vigil/MyNotificationListenerService.kt` — 监听/匹配/播放/唤醒锁/报警恢复/绑定看门狗（心跳中检测断连并自动 requestRebind → 组件 toggle 强刷）
  - `app/src/main/java/com/example/vigil/ListenerRecovery.kt` — 监听绑定自愈（requestRebind / 组件 toggle），Service 与 MainActivity 共用
  - `app/src/main/java/com/example/vigil/SharedPreferencesHelper.kt` — 全部持久化（关键词、铃声、未确认报警、listener_connected 绑定状态）
  - `app/src/main/java/com/example/vigil/PermissionUtils.kt` — 权限检查与引导
  - `app/src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt` — 服务状态/心跳/报警弹窗状态

## 真机测试流程（闭环测试标准操作）

### 环境

- Windows Git Bash：adb 命令含设备侧路径前先 `export MSYS_NO_PATHCONV=1`。
- Write 工具的 `/tmp` = Git Bash 的 `D:/tmp`，勿混用。

### 安装与权限

1. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. 通知监听：`adb shell cmd notification allow_listener com.example.vigil/com.example.vigil.MyNotificationListenerService`
   - 报 `service not found` → 查 `adb shell dumpsys package com.example.vigil` 的 `disabledComponents`；组件若是 App 自己禁用的，`pm enable` 会被 SecurityException 拒绝，须启动 App 让它自己 enable（`MainActivity.startVigilService`）。
3. 电池白名单（必做，否则 Device Guard 在报警约 30 秒后强杀进程）：
   `adb shell dumpsys deviceidle whitelist +com.example.vigil`
4. debug 包注入配置（免 UI）：`run-as com.example.vigil` 写 `/data/data/com.example.vigil/shared_prefs/vigil_prefs.xml`（keys：`keywords` StringSet、`service_enabled`、`is_first_launch`、`has_shown_donate_dialog`）

### 触发与验证

- 测试通知：`adb shell cmd notification post -t "标题" tag "正文"` —— 标题/正文不得含**空格**（多层 shell 转发会按空格拆参截断，导致关键词匹配不上、测试假阴性）；逗号（含中文逗号）实测无碍。
- 音量最小：`adb shell cmd media_session volume --stream 4 --set 1`（STREAM_ALARM=4；音量键不可靠，前台时调的是 MUSIC 流）。
- 播放中：`adb shell dumpsys media.player` 应见 `packageName: com.example.vigil`、`NuPlayer state(5)`（STARTED）、`looping(1)`、`stream type(4)`；停止后该条目消失。
- UI 按钮：`adb shell uiautomator dump` 取 `bounds` → `adb shell input tap x y`；截图 `screencap`；投屏 scrcpy 必须 `--no-audio`，用 `ADB=` 环境变量复用现有 adb server。

### 进程死亡排查与模拟

- 死因：`adb shell dumpsys activity exit-info com.example.vigil`（`reason=10` + `from ... uid 10223` = Motorola Device Guard 强杀）。
- 模拟：`adb shell am crash com.example.vigil`（debuggable 包）；shell `kill -9` 和 `run-as kill` 均无效。
- 禁止用 app_process 反射 @hide 类（抛异常即被 KillApplicationHandler 杀进程）。

## 最小验证矩阵

| 变更类型 | 最小验证 |
|---|---|
| 监听/匹配/播放逻辑 | 构建 + 真机闭环（触发 → `media.player` 验证响铃 → 弹窗 → 确认 → 验证停止） |
| 报警恢复/进程重启逻辑 | 闭环 + `am crash` 后验证服务重建恢复响铃、确认后 pending 清除 |
| 设置项/持久化 | 构建 + `run-as` 读 `vigil_prefs.xml` 核对写入 |
| 纯 UI | 构建 + 截图核对 |
| Manifest/权限 | 构建 + 真机对应权限流程走一遍 |

## 已知平台坑

- 系统（国产 ROM 尤甚）可能在进程被杀重建后不再重新绑定 NotificationListenerService，但 `enabled_notification_listeners` 设置仍在——权限检查与"进程活着"都不能证明监听在工作，唯一可信信号是 `onListenerConnected` 回调（持久化为 `listener_connected`）。自愈手段：`NotificationListenerService.requestRebind()`，失败时组件 toggle 强刷（等效用户撤销再授予权限）。
- Motorola Device Guard（`com.motorola.deviceguard`）把"前台服务 + 唤醒锁 + 循环响铃"判为耗电并强杀进程 —— 电池白名单是功能前提，设置页已有引导入口。
- Android 10+ 后台 `startActivity` 静默失败（不抛异常，`try/catch` 兜底不触发）：报警弹窗靠持久化 + App 打开时补弹（`MonitoringViewModel.init`）。
- `cmd notification`、`cmd media_session` 等 cmd 子命令各厂商可用性不同，用前先 `cmd <name> --help` 探明。

## 发布

- release 构建：`./gradlew assembleRelease`（minify + 资源压缩，签名配置在 `app/build.gradle.kts` 的 `signingConfigs.release`）。
- 发版动作：递增 `versionCode`/`versionName`，在 `README.md` 更新日志追加一节。

## 文档地图

- `README.md` — 功能、使用、权限说明、更新日志（功能与版本事实的唯一来源）。
- `AGENTS.md` — 协作与真机测试规则（本文件）。
- `design-backup/` — UI 设计稿存档（PDF/PEN），只读参考。
- 事实变化时只更新负责该事实的文档。
