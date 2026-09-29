# 设备验证手册

本页记录报警闭环测试和各类改动的最小验证范围。测试前先读[项目安全与协作细则](project-rules.md)。

## 真机测试流程（闭环测试标准操作）

### 验证设备选择

1. **有真机设备**（`adb devices` 有真机在线）→ 一律用真机。
2. **没有真机** → 退到模拟器，并**优先使用高版本 AVD**；需要验证老版本兼容行为时再开低版本。
3. 本机已有 AVD（模拟器二进制：`<sdk.dir>/emulator/emulator`，`sdk.dir` 见 `local.properties`；列表命令 `emulator -list-avds`）：
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
- **`adb root` 之后 `cmd notification post` 会静默失效**（2026-09-20 实测）：adbd 切到 root 后 shell 命令以 root 身份执行，NotificationService 报 `PackageManager$NameNotFoundException: root` → 「Cannot fix notification」，通知被直接丢弃、`dumpsys notification` 里连记录都没有，表现为"监听已连接但收不到任何通知"的假故障。测通知前先 `adb unroot`（`adb shell id` 应为 `uid=2000(shell)`）；需要 root 读写应用私有目录时，把 root 操作与发通知分开做。
- adb push 本地路径：开了 `MSYS_NO_PATHCONV=1` 后，Git Bash 风格 `/c/Users/...` 传给 Windows 版 adb 会报 cannot stat；本地侧路径一律写 Windows 形式（如 `C:\Users\xgwnj\AppData\Local\Temp\vigil_prefs.xml`），设备侧路径写 Linux 形式，互不冲突。
- 更新检查本地模拟：debug 构建用 `run-as` 写 `debug_update_api_base`（SharedPreferences）指向本地模拟 GitHub；debug 构建已允许 cleartext（`app/src/debug/AndroidManifest.xml` 的 `usesCleartextTraffic`，release 不合并）。坑：模拟器经 `10.0.2.2` 访问宿主的**大响应**（几百 KB 以上）会被截断（`unexpected end of stream`），改用 `adb reverse tcp:<port> tcp:<port>` + 基址写 `http://127.0.0.1:<port>` 走 adb 传输（实测可靠）；调起系统安装后 Play Protect 可能拦截 debug 包，属平台行为。

### 环境

- Windows Git Bash：adb 命令含设备侧路径前先 `export MSYS_NO_PATHCONV=1`。
- Write 工具的 `/tmp` 就是 Git Bash 的 `/tmp`（本机映射到 `C:/Users/xgwnj/AppData/Local/Temp`），勿混用。

### 安装与权限

1. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
   - 例外：主力机小米15 装的是 release 签名包，debug 包签名冲突装不上（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。有维护者安全提供的密钥备份时，可本地 `./gradlew assembleRelease` 后安装 release 包覆盖更新（数据无损）；普通本地检出没有密钥时，使用 CI 产出的同签名 APK。release 不可 debug，`run-as` 注入配置不可用。
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
| 报警恢复/进程重启逻辑 | 闭环 + `am crash` 后验证服务重建恢复队首响铃、确认后队首清除并推进下一项 |
| 报警队列/重复触发调度 | 构建 + `scripts/run-alert-stress.ps1`；核对 FIFO、同词聚合、冷却边界、崩溃后 ID/顺序、最终 MediaPlayer 释放 |
| 延时报警（策略/调度/恢复） | 构建 + 闭环（设备按上方规则选择）：debug 包注入 `default_delay_policy`，release 包通过设置页配置（FIXED 1 分钟或 SCHEDULED 取本地时间 +2 分钟）→ 发通知 → debug 包用 `run-as` 读 `scheduled_alerts`，release 包用待触发清单与诊断日志核对到期时刻且**立即不响铃**（`dumpsys media.player` 无条目）→ `dumpsys alarm` 见本应用闹钟 → 到点后 `media.player` 响铃 → 弹窗确认停铃；debug 包再补验 `am crash` 后服务重建仍按时触发、关服务开关取消待触发项、以及精确/非精确两条路径（切换方法见[平台兼容经验](platform.md)） |
| 设置项/持久化 | 构建 + `run-as` 读 `vigil_prefs.xml` 核对写入 |
| 纯 UI | 构建 + 截图核对 |
| Manifest/权限 | 构建 + 真机（或模拟器）对应权限流程走一遍 |
