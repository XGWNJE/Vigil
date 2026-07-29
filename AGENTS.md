# Vigil 项目规则

Android 关键词通知报警应用（Kotlin + Jetpack Compose，MVVM）。
核心链路：通知命中关键词 → 循环播放闹钟铃声 + 唤醒锁 + 应用内全屏弹窗 → 用户确认后停止。

## 静默观察（harness-observer，常驻职责）

- 若你的系统提示已注入全局协作规则（Claude Code / Codex / OpenCode 会自动注入），其中已含本职责，跳过本节。
- 否则（如 Kimi Code）：干活全程留意四类信号——owner 重复纠正、owner 明确表达的偏好、可复用踩坑、你自身的语义失败（假设/目标/路径/范围错误）。
- 命中即静默追加到 `D:\ObjectCode\HarnessOS\notes\inbox\YYYY-MM-DD.md`（先查重，同主题重复计数 +1，脱敏）；记录格式与验收标准以 harness-observer skill 为准，需要时加载它。
- 静默 = 不打断当前任务、不在对话中提及、不改动当前项目任何文件；只追加 inbox，归并提炼留给 owner 评审。

## 铁律（不可违反）

1. 真机测试不得让设备实际出声：先调最小音量，一律用 `dumpsys media.player` 验证播放/停止，不依赖人耳。
2. 改动报警核心链路（通知匹配 → 响铃 → 弹窗 → 确认停止）后，必须跑完整闭环测试才允许交付（验证设备选择见「真机测试流程」：有真机用真机，无真机退模拟器并优先高版本）；不接受纯静态检查结论。
3. 报警状态必须持久化（SharedPreferences），不得只存内存：厂商省电策略（Motorola Device Guard 等）随时可能强杀进程，内存状态 = 报警丢失。
4. 不得删除或丢失 `keystore/vigil.keystore`（2026-07 轮换后的新密钥）：release 签名依赖它，丢失则无法发布更新包。旧密钥曾意外入库+密码明文，公网视为泄露，已轮换并改名 `keystore/OLD-COMPROMISED-DO-NOT-USE.keystore` 存档（gitignored，仅存档不得使用）。签名密码只放 `keystore.properties`（gitignored）或 CI 环境变量（`VIGIL_STORE_PASSWORD`/`VIGIL_KEY_ALIAS`/`VIGIL_KEY_PASSWORD`），**任何密钥文件与密码都不得提交进仓库**。
5. `VigilEventBus` 除 `heartbeat` 外均为无 replay 的 SharedFlow，进程重建后事件即丢；任何"服务 → UI"的关键事件都必须有持久化兜底。`heartbeat` 例外：`replay=1` 且 payload 携带发射时刻时间戳（`elapsedRealtime`），收集方按时间戳算年龄，陈旧 replay 不会掩盖服务已死。

## 关键路径与命令

- 构建：`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- 版本号：`app/build.gradle.kts` 的 `versionCode`/`versionName`
- 核心代码：
  - `app/src/main/java/com/example/vigil/MyNotificationListenerService.kt` — 监听/匹配/播放/唤醒锁/报警恢复/绑定看门狗（心跳中检测断连并自动 requestRebind → 组件 toggle 强刷）
  - `app/src/main/java/com/example/vigil/ListenerRecovery.kt` — 监听绑定自愈（requestRebind / 组件 toggle），Service 与 MainActivity 共用
  - `app/src/main/java/com/example/vigil/SharedPreferencesHelper.kt` — 全部持久化（关键词、铃声、未确认报警、listener_connected 绑定状态）
  - `app/src/main/java/com/example/vigil/PermissionUtils.kt` — 权限检查与引导
  - `app/src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt` — 服务状态/心跳/报警弹窗状态
  - `app/src/main/java/com/example/vigil/VigilLogger.kt` — 持久化诊断日志（filesDir/logs/vigil.log，1MB 滚动双文件；每条立即 flush）+ 导出拼接（诊断头 + old + 当前），主屏设置 Sheet「导出日志」行经 FileProvider 分享；不写通知正文

## 真机测试流程（闭环测试标准操作）

### 验证设备选择

1. **有真机设备**（`adb devices` 有真机在线）→ 一律用真机。
2. **没有真机** → 退到模拟器，并**优先使用高版本 AVD**；需要验证老版本兼容行为时再开低版本。
3. 本机已有 AVD（模拟器二进制：`<sdk.dir>/emulator/emulator`，`sdk.dir` 见 `local.properties`，当前为 `C:\Users\Administrator\AppData\Local\Android\Sdk`；列表命令 `emulator -list-avds`）：
   - `VisionGuard_API36` — Android 16（API 36），x86_64，google_apis_playstore（默认首选）
   - `Pixel_3a_XL` — Android 9（API 28），x86，google_apis（老版本兼容验证）
4. 启动（后台进程）：`emulator -avd <名字> -no-boot-anim -no-snapshot-save`；等开机完成：`adb wait-for-device` 后轮询 `adb shell getprop sys.boot_completed` 直到输出 1；关闭：`adb -s <serial> emu kill`。
5. 多设备/多模拟器并存时用 `adb -s emulator-55xx` 指定目标。

### 模拟器专属手段（与真机的差异点）

- 测试通知源：`cmd notification post` 仅高版本有（API 36 实测可用，API 28 实测无此子命令）；老版本用 `adb emu sms send <号码> <关键词>` 让系统短信应用代发通知（标题=号码，正文=短信内容）。真机则发自定义短信/邮件。
  - 坑：同一会话的后续短信复用同一通知 key，会命中应用内"已报警过"去重导致不再触发；换下一轮测试前先 `am crash` 重启进程或在通知栏划掉该通知。
- 勿扰控制（验证"勿扰下按系统策略响铃"用）：API 36 用 `cmd notification set_dnd on|none|priority|alarms|all|off`；`settings put global zen_mode` 在 API 28/36 实测均被静默忽略；API 28 无 `set_dnd`，走 UI 自动化：`am start -a android.settings.ZEN_MODE_SETTINGS` → uiautomator 点「立即开启」，进「Sound & vibration」行为页可关「闹钟」例外构造压制场景。闹钟流是否被压看 `dumpsys audio` 的 `STREAM_ALARM: Muted: true/false`。
- `dumpsys media.player` 版本差异：API ~29 及以下**没有 packageName 归因行**，改用 `state(5)`（STARTED）+ `stream type(4)` 计数判断在播/已停。
- 连续多次 `am crash` 会触发系统「屡次停止运行」对话框并阻止应用重启，需 uiautomator 点「关闭应用」后再拉起。
- adb push 本地路径：开了 `MSYS_NO_PATHCONV=1` 后，Git Bash 风格 `/d/tmp/...` 传给 Windows 版 adb 会报 cannot stat；本地侧路径一律写 Windows 形式（如 `D:\tmp\vigil_prefs.xml`），设备侧路径写 Linux 形式，互不冲突。

### 环境

- Windows Git Bash：adb 命令含设备侧路径前先 `export MSYS_NO_PATHCONV=1`。
- Write 工具的 `/tmp` = Git Bash 的 `D:/tmp`，勿混用。

### 安装与权限

1. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
   - 例外：主力机小米15 装的是 release 签名包，debug 包签名冲突装不上（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），改走 `./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk` 覆盖更新（数据无损；release 不可 debug，`run-as` 注入配置不可用）。
2. 通知监听：`adb shell cmd notification allow_listener com.example.vigil/com.example.vigil.MyNotificationListenerService`
   - 报 `service not found` → 查 `adb shell dumpsys package com.example.vigil` 的 `disabledComponents`；组件若是 App 自己禁用的，`pm enable` 会被 SecurityException 拒绝，须启动 App 让它自己 enable（`MainActivity.startVigilService`）。
3. 电池白名单（必做，否则 Device Guard 在报警约 30 秒后强杀进程）：
   `adb shell dumpsys deviceidle whitelist +com.example.vigil`
4. debug 包注入配置（免 UI）：`run-as com.example.vigil` 写 `/data/data/com.example.vigil/shared_prefs/vigil_prefs.xml`（keys：`keywords` StringSet、`service_enabled`、`is_first_launch`、`has_shown_donate_dialog`）
   - 防注入被覆盖：注入前先 `cmd notification disallow_listener ...` + `am force-stop`（否则 force-stop 后系统瞬间重绑监听、新进程用启动时加载的内存 prefs 回写，把注入内容冲掉），注入验证通过后再 allow_listener 启动。

### 触发与验证

- 测试通知：`adb shell cmd notification post -t "标题" tag "正文"` —— 标题/正文不得含**空格**（多层 shell 转发会按空格拆参截断，导致关键词匹配不上、测试假阴性）；逗号（含中文逗号）实测无碍。
- 音量最小：`adb shell cmd media_session volume --stream 4 --set 1`（STREAM_ALARM=4；音量键不可靠，前台时调的是 MUSIC 流）。
- 播放中：`adb shell dumpsys media.player` 应见 `packageName: com.example.vigil`、`NuPlayer state(5)`（STARTED）、`looping(1)`、`stream type(4)`；停止后该条目消失。
- UI 按钮：`adb shell uiautomator dump` 取 `bounds` → `adb shell input tap x y`；截图 `screencap`；投屏 scrcpy 必须 `--no-audio --keyboard=sdk`（v4 默认模拟物理键盘会把设备软键盘藏起来，sdk 模式才能正常调出输入法；电脑端打中文是 scrcpy 本身限制，在投屏里点手机键盘输入），用 `ADB=` 环境变量复用现有 adb server。
- 诊断日志：debug 包可 `run-as com.example.vigil cat /data/data/com.example.vigil/files/logs/vigil.log` 直读（含进程启动标记、绑定/断连、看门狗、通知处理结果、报警链路）；release 包让用户在主屏设置 Sheet 点「导出日志」分享导出。

### 进程死亡排查与模拟

- 死因：`adb shell dumpsys activity exit-info com.example.vigil`（`reason=10` + `from ... uid 10223` = Motorola Device Guard 强杀）。
- 模拟：`adb shell am crash com.example.vigil`（debuggable 包）；shell `kill -9` 和 `run-as kill` 均无效。
- 禁止用 app_process 反射 @hide 类（抛异常即被 KillApplicationHandler 杀进程）。

## 最小验证矩阵

| 变更类型 | 最小验证 |
|---|---|
| 监听/匹配/播放逻辑 | 构建 + 闭环（真机优先，无真机用高版本模拟器：触发 → `media.player` 验证响铃 → 弹窗 → 确认 → 验证停止） |
| 报警恢复/进程重启逻辑 | 闭环 + `am crash` 后验证服务重建恢复响铃、确认后 pending 清除 |
| 设置项/持久化 | 构建 + `run-as` 读 `vigil_prefs.xml` 核对写入 |
| 纯 UI | 构建 + 截图核对 |
| Manifest/权限 | 构建 + 真机（或模拟器）对应权限流程走一遍 |

## 已知平台坑

- 系统（国产 ROM 尤甚）可能在进程被杀重建后不再重新绑定 NotificationListenerService，但 `enabled_notification_listeners` 设置仍在——权限检查与"进程活着"都不能证明监听在工作，唯一可信信号是 `onListenerConnected` 回调（持久化为 `listener_connected`）。自愈手段：`NotificationListenerService.requestRebind()`，失败时组件 toggle 强刷（等效用户撤销再授予权限）。
- Motorola Device Guard（`com.motorola.deviceguard`）把"前台服务 + 唤醒锁 + 循环响铃"判为耗电并强杀进程 —— 电池白名单是功能前提，设置页已有引导入口。
- Android 10+ 后台 `startActivity` 静默失败（不抛异常，`try/catch` 兜底不触发）：报警弹窗靠持久化 + App 打开时补弹（`MonitoringViewModel.init`）。
- `cmd notification`、`cmd media_session` 等 cmd 子命令各厂商可用性不同，用前先 `cmd <name> --help` 探明。
- Compose `rememberInfiniteTransition`/`animate*AsState` 会被「开发者选项 → 动画程序时长缩放 = 关闭」挂起（Compose 把 animator_duration_scale 读进 MotionDurationScale）。实测案例（v1.8.0 小米真机）：首页涟漪单机静止，模拟器与另一台平板正常；装帧驱动修复包（v1.8.1）后立即恢复，坐实根因是该设置。关键状态动效（首页涟漪/核心呼吸）因此改用 `withFrameNanos` 帧驱动（`RippleBackground.kt` 的 `rememberFrameDrivenProgress`），不受该设置影响。真机"单机异常"先做对照（模拟器/另一台真机/构建变体）再升级假设。
- Android 15+（targetSdk 35+）禁止应用修改全局勿扰状态：`setInterruptionFilter`/`setNotificationPolicy` 只会创建/更新应用名下的隐式 AutomaticZenRule，按"最严格策略胜出"合并——既突破不了用户手动开启的勿扰，且隐式规则可能在用户手动关闭勿扰后继续强加全局静默（实测 API 36 模拟器：调用 setInterruptionFilter(NONE) 后，用户手动关勿扰设备仍完全静音）。项目因此移除了勿扰穿透功能：不申请勿扰访问权限、不干预用户勿扰设置，勿扰下按系统当前策略响铃（官方说明：https://developer.android.com/about/versions/15/behavior-changes-15#dnd-changes ）。

## 发布

- release 构建：`./gradlew assembleRelease`（minify + 资源压缩，签名配置在 `app/build.gradle.kts` 的 `signingConfigs.release`，密码读 `keystore.properties` 或环境变量兜底）。
- 发版动作：递增 `versionCode`/`versionName`（README 已无更新日志板块，无需维护变更记录）。
- CI：`.github/workflows/release.yml` 在推 `v*` tag 时自动构建 release APK 并创建 GitHub Release；依赖仓库 secrets `VIGIL_KEYSTORE_BASE64`（keystore base64）、`VIGIL_STORE_PASSWORD`、`VIGIL_KEY_ALIAS`、`VIGIL_KEY_PASSWORD`。
- 商店素材：`store/`（中英文案、权限用途说明表、feature-graphic.png）、隐私政策 `PRIVACY.md`（Play Console 隐私政策 URL 直接用它的 GitHub 链接）。
- 已移除 `QUERY_ALL_PACKAGES`（Play 高敏感权限）：应用过滤改用 launcher intent 查询，只列桌面可见应用。

## 文档地图

- `README.md` — 功能、使用、权限说明（功能事实的唯一来源）。
- `PRIVACY.md` — 中英双语隐私政策（商店表单直接引用其 GitHub 链接）。
- `store/README.md` — 商店上架文案与权限用途说明表。
- `AGENTS.md` — 协作与真机测试规则（本文件）。
- `design-backup/` — UI 设计稿存档（PDF/PEN），只读参考。
- 事实变化时只更新负责该事实的文档。
