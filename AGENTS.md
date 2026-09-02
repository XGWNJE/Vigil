# Vigil 项目规则

Android 关键词通知报警应用（Kotlin + Jetpack Compose，MVVM）。
核心链路：通知命中关键词 → 循环播放闹钟铃声 + 唤醒锁 + 应用内全屏弹窗 → 用户确认后停止。

## 静默观察（harness-observer，常驻职责）

- 若你的系统提示已注入全局协作规则（Claude Code / Codex / OpenCode 会自动注入），其中已含本职责，跳过本节。
- 否则（如 Kimi Code）：干活全程留意四类信号——owner 重复纠正、owner 明确表达的偏好、可复用踩坑、你自身的语义失败（假设/目标/路径/范围错误）。
- 命中即静默追加到 `D:\ObjectCode\HarnessOS\notes\inbox\YYYY-MM-DD.md`（先查重，同主题重复计数 +1，脱敏）；记录格式与验收标准以 harness-observer skill 为准，需要时加载它。
- 静默 = 不打断当前任务、不在对话中提及、不改动当前项目任何文件；只追加 inbox，归并提炼留给 owner 评审。

## 中转站真机验证（常驻职责）

- 若你的系统提示已注入全局协作规则（Claude Code / Codex / OpenCode 会自动注入），其中已含本职责，跳过本节。
- 否则（如 Kimi Code）：开发内容未外部发版、仍在测试阶段，且发现用户在用模拟器/虚拟机调试验证、未连接真机时，主动询问用户是否把构建产物传到中转站（get.xgwnje.cn），让用户自行安装到真机测试；问过才传，不擅自上传。

## 铁律（不可违反）

1. 真机测试不得让设备实际出声：先调最小音量，一律用 `dumpsys media.player` 验证播放/停止，不依赖人耳。
2. 改动报警核心链路（通知匹配 → 响铃 → 弹窗 → 确认停止）后，必须跑完整闭环测试才允许交付（验证设备选择见「真机测试流程」：有真机用真机，无真机退模拟器并优先高版本）；不接受纯静态检查结论。
3. 报警状态必须持久化（SharedPreferences），不得只存内存：厂商省电策略（Motorola Device Guard 等）随时可能强杀进程，内存状态 = 报警丢失。
4. 不得删除或丢失 `keystore/vigil.keystore`（2026-07 轮换后的新密钥）：release 签名依赖它，丢失则无法发布更新包。旧密钥曾意外入库+密码明文，公网视为泄露，已轮换并改名 `keystore/OLD-COMPROMISED-DO-NOT-USE.keystore` 存档（gitignored，仅存档不得使用）。签名密码只放 `keystore.properties`（gitignored）或 CI 环境变量（`VIGIL_STORE_PASSWORD`/`VIGIL_KEY_ALIAS`/`VIGIL_KEY_PASSWORD`），**任何密钥文件与密码都不得提交进仓库**。
5. `VigilEventBus` 除 `heartbeat` 外均为无 replay 的 SharedFlow，进程重建后事件即丢；任何"服务 → UI"的关键事件都必须有持久化兜底。`heartbeat` 例外：`replay=1` 且 payload 携带发射时刻时间戳（`elapsedRealtime`），收集方按时间戳算年龄，陈旧 replay 不会掩盖服务已死。
6. 每次提交 / push / 收口任务前必须对齐文档：事实变了就同步更新对应文档。README 始终保持简洁，不重要的内容不写进去；不是特别重要但有必要记住的东西，写进本文件（AGENTS.md）或其他专门文档，不堆在 README。

## 关键路径与命令

- 构建：`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- 版本号：`app/build.gradle.kts` 的 `versionCode`/`versionName`
- 核心代码：
  - `app/src/main/java/com/example/vigil/MyNotificationListenerService.kt` — 监听/匹配/播放/唤醒锁/报警恢复/绑定看门狗（心跳中检测断连，触发 `ListenerRecovery` 快速自愈）；循环次数有限档位不用 isLooping，改 OnCompletion 手动续播计数（每次续播重新 acquire wakelock 续期），到数自动结束（写记录 → 清 pending → 停铃 → emit alertAutoEnded 关弹窗）；响铃期间同一前台服务通知 ID 切换到高优先级报警渠道，点击携带持久化关键词进入处理，结束后切回普通监听通知
  - `app/src/main/java/com/example/vigil/ListenerRecovery.kt` — 监听绑定自愈（快速自愈：requestRebind → 短观察 → 完整重连序列 → 无效即标记 `listener_recovery_failed`），Service 与 MainActivity 共用
  - `app/src/main/java/com/example/vigil/RingtoneLibrary.kt` — 铃声库（P2 自定义铃声来源）+ 内置预设（随 APK 打包）：导入 SAF 音频复制到 `filesDir/ringtones/`、录音（MediaRecorder m4a/AAC）、试听、删除；铃声值约定 `content://` = 系统铃声 / `android.resource://<pkg>/raw/<name>` = 内置预设（res/raw WAV，不可删除，owner 提供 TTS 语音，MP3 因质量未采用）/ 其他非空 = 库内文件绝对路径 / null = 系统默认闹钟；`resolve()` 解析播放数据源（预设经 AssetFileDescriptor 播放——见「已知平台坑」），文件缺失回落并写日志；试听状态变化经 `onPreviewStateChanged` 回调刷新 UI
  - `app/src/main/java/com/example/vigil/SharedPreferencesHelper.kt` — 全部持久化（关键词、默认铃声、关键词级铃声/循环次数映射 `keyword_ringtones`/`keyword_loop_counts`、全局默认循环次数 `default_loop_count`（1–10；旧版无限/越界值迁移到范围内）、未确认报警 pending（含铃声 URI/loopLimit/已播次数/来源应用）、报警历史 `alert_history`（JSON，上限 100 条）、listener_connected 绑定状态）
  - `app/src/main/java/com/example/vigil/PermissionUtils.kt` — 权限检查与引导
  - `app/src/main/java/com/example/vigil/ui/monitoring/MonitoringViewModel.kt` — 服务状态/心跳/报警弹窗状态
  - `app/src/main/java/com/example/vigil/VigilLogger.kt` — 持久化诊断日志（filesDir/logs/vigil.log，1MB 滚动双文件；每条立即 flush）+ 导出拼接（诊断头 + old + 当前），主屏设置 Sheet「导出日志」行经 FileProvider 分享；不写通知正文
  - `app/src/main/java/com/example/vigil/VigilEventBus.kt` — 进程内事件总线（SharedFlow，replay 语义与持久化兜底要求见铁律 5）
  - `app/src/main/java/com/example/vigil/ui/settings/SettingsViewModel.kt` — 关键词/铃声/应用过滤状态与持久化；过滤列表排序：已勾选 → 用户应用优先 → 名称（`appListComparator`，初始加载与勾选切换共用）
  - `app/src/main/java/com/example/vigil/MainActivity.kt` — Compose 根宿主，生命周期管理，服务启停，报警弹窗承载；冷启动触发自动更新检查，并处理「安装未知应用」权限与安装调起
  - `app/src/main/java/com/example/vigil/UpdateChecker.kt` — GitHub 渠道自动更新：`GET /releases/latest` 解析最新版（版本号/发版说明/APK 资产直链），耐用的版本号比较（`isNewer`），APK 下载到 cacheDir、FileProvider 授权调起系统安装。刻意用内置 `HttpURLConnection` + `org.json`，不引第三方库（保极致轻量）。debug 构建可用 `debug_update_api_base`（SharedPreferences）覆盖 API 基址给本地模拟（生产恒访问 GitHub 且仅 HTTPS）
  - `app/src/main/java/com/example/vigil/UpdateViewModel.kt` — 自动更新状态机（冷启动/手动检查、更新弹窗、下载进度、安装就绪）；已点「稍后」的版本记入 `last_dismissed_update_version`，冷启动不再重复提示
- UI 页面：`MainScreen`（涟漪状态首页；设置 Sheet 双入口：顶栏齿轮 + 上滑手势（底部发丝线把手常驻，首次启动显示「上滑打开设置」，Sheet 打开过一次即收起）；Sheet 内容：关键词、铃声、应用过滤、权限三级分组（REQUIRED 通知使用权 / RECOMMENDED 电池白名单 + 锁定任务卡片引导（「不再提示」后整行隐藏，`lock_task_tip_dismissed`）/ OPTIONAL 自启动管理、后台运行）、导出日志、开源地址、检查更新（版本号文本，点击触发手动检查）、`AppFilterScreen`（应用过滤全屏页：搜索、SYS 标记、多选、勾选置顶）、`KeywordAlertDialog`（命中全屏弹窗，确认停铃，内容居中避开导航栏）、`PermissionGuideDialog`（权限引导确认弹窗，确认后跳系统设置）、`ui/dialogs/UpdateDialog.kt`（自动更新弹窗：发现新版本展示发版说明 + 更新/稍后；已最新 / 无法访问 GitHub / 异常 各有提示；下载进度）
- 图标资产：launcher icon 内容不得顶边——缩放约 75% 居中、四边预留 ≥15% 安全边距，导出 mipmap 前做圆角 mask 预演（系统圆角 mask 会裁切顶边内容）。来源：notes/inbox/2026-07-27.md，owner 2026-07-30 验收
- 设计主题「一线」：极简深色、发丝线分区、无卡片、单一强调色。背景 #0A0A0B / 文字 #EAEAE7 / 分割线 #1F1F23 / 主色 #E4FF54 酸橙绿 / 警示 #FFB020 琥珀

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
- 更新检查本地模拟：debug 构建用 `run-as` 写 `debug_update_api_base`（SharedPreferences）指向本地模拟 GitHub；debug 构建已允许 cleartext（`app/src/debug/AndroidManifest.xml` 的 `usesCleartextTraffic`，release 不合并）。坑：模拟器经 `10.0.2.2` 访问宿主的**大响应**（几百 KB 以上）会被截断（`unexpected end of stream`），改用 `adb reverse tcp:<port> tcp:<port>` + 基址写 `http://127.0.0.1:<port>` 走 adb 传输（实测可靠）；调起系统安装后 Play Protect 可能拦截 debug 包，属平台行为。

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
  - **[HyperOS 3.0.308 实机证据 2026-08（issue #2 日志）]**：应用开关关闭再打开后，`requestRebind()` 被系统静默忽略（连续多次调用、永不回调 onListenerConnected），唯一有效恢复是系统级撤销+重新授权（用户卸载重装/清数据重配即此效果）。因此检测到绑定断开（`onListenerDisconnected` 或看门狗）后走「快速自愈」：先无损 `requestRebind` → 短观察（2.5s）→ 完整重连序列（stopService → 组件 disable → 1.5s → enable → 重启服务 → requestRebind，`ListenerRecovery.runForceReconnectSequence`，须在独立作用域执行——序列会 stopService 销毁服务，用服务自己的 scope 会在 onDestroy 时 cancel 中断）→ 观察（4s）；序列仍无效则**立即**持久化 `listener_recovery_failed` 标记（从断开到标记失败约 10s，不做长时间重连，避免用户误以为卡死）→ UI 显示「立即重试 / 重新授权」逃生通道（跳系统「通知使用权」设置页），用户无需再卸载重装。恢复成功（`onListenerConnected`）即清除该标记。
- Motorola Device Guard（`com.motorola.deviceguard`）把"前台服务 + 唤醒锁 + 循环响铃"判为耗电并强杀进程 —— 电池白名单是功能前提，设置页已有引导入口。
- 小米 HyperOS（真机实测）：应用内「电池白名单」（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）与「后台运行」（应用详情→省电策略）两个入口同质——电池优化请求被重定向到小米自家省电策略页；白名单设成功后后台相对稳定，但任务卡片（recents）不锁定 + 未开自启动时，用户划掉卡片进程仍会被杀。原生 Android 上两入口不同质（系统弹窗 vs 应用详情页），华为/OPPO/vivo 的 OEM 后台限制独立于标准白名单 → 跨 ROM 的权限引导设计必须同时保留两个入口，不因单一 ROM 观察而合并。来源：notes/inbox/2026-07-30.md，owner 2026-07-31 验收。
- Android 10+ 后台 `startActivity` 静默失败（不抛异常，`try/catch` 兜底不触发）：报警弹窗靠持久化 + App 打开时补弹（`MonitoringViewModel.init`）。
- `cmd notification`、`cmd media_session` 等 cmd 子命令各厂商可用性不同，用前先 `cmd <name> --help` 探明。
- Compose `rememberInfiniteTransition`/`animate*AsState` 会被「开发者选项 → 动画程序时长缩放 = 关闭」挂起（Compose 把 animator_duration_scale 读进 MotionDurationScale）。实测案例（v1.8.0 小米真机）：首页涟漪单机静止，模拟器与另一台平板正常；装帧驱动修复包（v1.8.1）后立即恢复，坐实根因是该设置。关键状态动效（首页涟漪/核心呼吸）因此改用 `withFrameNanos` 帧驱动（`RippleBackground.kt` 的 `rememberFrameDrivenProgress`），不受该设置影响。真机"单机异常"先做对照（模拟器/另一台真机/构建变体）再升级假设。
- Android 15+（targetSdk 35+）禁止应用修改全局勿扰状态：`setInterruptionFilter`/`setNotificationPolicy` 只会创建/更新应用名下的隐式 AutomaticZenRule，按"最严格策略胜出"合并——既突破不了用户手动开启的勿扰，且隐式规则可能在用户手动关闭勿扰后继续强加全局静默（实测 API 36 模拟器：调用 setInterruptionFilter(NONE) 后，用户手动关勿扰设备仍完全静音）。项目因此移除了勿扰穿透功能：不申请勿扰访问权限、不干预用户勿扰设置，勿扰下按系统当前策略响铃（官方说明：https://developer.android.com/about/versions/15/behavior-changes-15#dnd-changes ）。
- **TTS 工具产出的 WAV 常带"未最终化"头**：RIFF/data 块长度字段为 0xFFFFFFFF 占位（v1.14.0 内置预设 6 条全中招），MediaPlayer 直接解析报 what=1 播放失败；修复 = 补写两个块长（`DataChunkSize = fileSize - dataChunkOffset - 8`）。入库前用十六进制/块遍历验证块长，不要只看 RIFF/WAVE 魔数。
- **`android.resource://` URI 播放在部分平台不可靠**：实测 API 36 模拟器 `MediaPlayer.setDataSource(context, "android.resource://pkg/raw/x")` 报 what=1 失败；统一改用 `resources.openRawResourceFd(resId)` → `setDataSource(fd, offset, length)`（先 `getIdentifier` 兜底资源缺失回落默认闹钟），raw 资源用 R.raw 引用防 shrinkResources 剥离。
- **系统闹钟文件可能自带 `autoLoop` 元数据**：API 36 模拟器的默认闹钟即使命令设置 `MediaPlayer.isLooping=false`，`dumpsys media.player` 仍显示 `autoLoop(1)` 且不触发 `OnCompletion`。有限次数播放必须同时按 `MediaPlayer.duration` 安排完成兜底；正常 `OnCompletion` 与时长兜底共用同一计数入口并互相取消，停止播放时清理延迟回调。

## 发布

- release 构建：`./gradlew assembleRelease`（minify + 资源压缩，签名配置在 `app/build.gradle.kts` 的 `signingConfigs.release`，密码读 `keystore.properties` 或环境变量兜底）。
- 发版动作：递增 `versionCode`/`versionName`（README 已无更新日志板块，无需维护变更记录）。
- CI：`.github/workflows/release.yml` 在推 `v*` tag 时自动构建 release APK 并创建 GitHub Release；依赖仓库 secrets `VIGIL_KEYSTORE_BASE64`（keystore base64）、`VIGIL_STORE_PASSWORD`、`VIGIL_KEY_ALIAS`、`VIGIL_KEY_PASSWORD`。

### GitHub 发布标准流程（owner 明确说"发布到 GitHub"时默认执行，无需逐步再确认）

owner 的发布指令即授权该流程内的全部 git 操作（commit / tag / push）。按序执行：

**铁律：Release 只由 CI 创建**——发版 = 推 tag 触发 workflow，禁止手动 `gh release create` / 网页建 Release / 手动传 APK 资产。手动创建会抢占同名 Release，CI 最后一步必报 `a release with the same tag name already exists`（2026-08-01 v1.11.0 教训：手动抢建导致 CI 失败；处置 = 删手动 Release（保留 tag）→ `gh run rerun` 让 CI 接管）。

1. **收口待发版改动**：对齐文档（铁律 6）、确认 `versionCode`/`versionName` 已递增；按 `release-notes/TEMPLATE.md` 固定格式写 `release-notes/vX.Y.Z.md`（CI 自动用作 Release 描述，缺失时回退 `--generate-notes` 只有 compare 链接）；提交并 `git push origin main`。
2. **检查 CI secrets**：`gh secret list --repo XGWNJE/Vigil` 必须有上述 4 个。缺失则按下方「secrets 重设」补齐（2026-07-30 教训：密钥轮换后 secrets 全缺，v1.8.2/v1.8.3 发版失败，报 `Tag number over 30 is not supported`）。
3. **打 tag 触发**：`git tag vX.Y.Z`（与 versionName 一致）→ `git push origin vX.Y.Z`。
4. **盯运行**：`gh run watch --exit-status` 盯到结束；失败用 `gh run view <id> --log-failed` 定位，修复后删远端 tag 重推（`git push origin :vX.Y.Z && git push origin vX.Y.Z`）或打新 tag。
5. **验收（全部通过才算完成）**：`gh release view vX.Y.Z` 确认 Release 与 APK 资产存在；下载 APK 跑 `apksigner verify --print-certs`，证书 SHA-256 必须等于本地 `keystore/vigil.keystore` 指纹（`keytool -list` 可查）。

### CI secrets 重设（缺失时）

```bash
# base64 单行编码，上传前本地回环验证（base64 -d 后与源文件 cmp 一致）
base64 -w 0 keystore/vigil.keystore > /tmp/ks.b64
base64 -d /tmp/ks.b64 | cmp keystore/vigil.keystore - && gh secret set VIGIL_KEYSTORE_BASE64 --repo XGWNJE/Vigil < /tmp/ks.b64
rm -f /tmp/ks.b64
# 密码取自 keystore.properties，printf 管道传入不回显；alias 固定 vigil
printf '%s' "$pw" | gh secret set VIGIL_STORE_PASSWORD --repo XGWNJE/Vigil
printf '%s' "$kp" | gh secret set VIGIL_KEY_PASSWORD --repo XGWNJE/Vigil
printf '%s' "vigil" | gh secret set VIGIL_KEY_ALIAS --repo XGWNJE/Vigil
```
- 商店素材：`store/`（中英文案、权限用途说明表、feature-graphic.png）、隐私政策 `PRIVACY.md`（Play Console 隐私政策 URL 直接用它的 GitHub 链接）。
- 已移除 `QUERY_ALL_PACKAGES`（Play 高敏感权限）：应用过滤改用 launcher intent 查询，只列桌面可见应用。**注意**：Android 11+ 应用可见性要求 manifest 用 `<queries>` 块声明该 launcher intent（`AndroidManifest.xml`），否则 queryIntentActivities 只能看到极少数应用（曾漏声明导致真机列表只剩 4 个、微信不可见）。

## 文档地图

- `README.md` — 功能、使用、权限说明（功能事实的唯一来源）。
- `PRIVACY.md` — 中英双语隐私政策（商店表单直接引用其 GitHub 链接）。
- `store/README.md` — 商店上架文案与权限用途说明表。
- `store/owner-checklist.md` — 上架执行清单（secrets / Play / 国内平台，需 owner 本人操作）。
- `AGENTS.md` — 协作与真机测试规则（本文件）。
- `ROADMAP.md` — 已确认的近期规划与已完成里程碑；未确认设想不进入路线图。
- `release-notes/` — 各版本发行描述（`vX.Y.Z.md`，CI 创建 Release 时自动引用）。
- `design/` — 图标与视觉素材（含生成脚本）；`design-backup/` — UI 设计稿存档（PDF/PEN），只读参考。
- `screenshots/` — README 界面展示与商店共用素材（`main/alert/app-filter.png` + `demo.gif`，语义命名）；制作规范：裁掉状态栏/手势条等系统元素、聚焦主体，动效录屏转 GIF（裁剪 + ≤10s + ≤5MB）。
- 事实变化时只更新负责该事实的文档。
