# 设备验证手册

操作规则见[项目细则](project-rules.md)。模拟器结果不能替代真机或厂商系统验收。

## 环境与边界

1. 默认有真机时由用户选择执行者；当前任务已授权优先使用可见的平板模拟器。纯构建和只读查询不需要操作设备 UI。
2. AVD 列表：`emulator -list-avds`；当前本机 `VisionGuard_Tablet_API36` 为 Android 16、2560×1600、320 dpi。工具路径由 Android SDK 配置确定，不能假定其他机器有同名 AVD。
3. 启动可见窗口：`emulator -avd <name> -no-boot-anim -no-snapshot-save -no-audio`。等待 `adb -s <serial> shell getprop sys.boot_completed` 输出 1，结束后 `adb -s <serial> emu kill`。
4. 所有操作明确指定 serial。不擅自修改设备锁屏、休眠或唤醒设置；真机无法正常唤醒时停下，等待用户解锁。
5. 真机先由用户确认不会出声，再检查 `dumpsys audio` 的 `STREAM_ALARM` 静音状态。最小音量不等于静音；无法确认时不触发报警。模拟器通过 `-no-audio` 禁止宿主音频输出。

## 构建与安装

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

签名不一致时不能覆盖安装。真机保留已有数据，使用同签名包；没有签名密钥的普通检出仅构建 Debug，不删除真机应用来绕过签名检查。

- 授权监听：`adb -s <serial> shell cmd notification allow_listener com.example.vigil/com.example.vigil.MyNotificationListenerService`。
- 组件禁用时启动应用，让应用恢复组件；不要以权限设置存在作为监听工作的证明。
- 模拟器可按场景加入电池白名单：`adb -s <serial> shell dumpsys deviceidle whitelist +com.example.vigil`。厂商系统后台限制仍需用户验收。
- 调试配置用 `run-as com.example.vigil`。注入前先撤销监听并 force-stop，避免运行中的内存设置覆盖文件；注入后重新授权并等待实际连接。

## 报警闭环

1. 开启监听，设置测试关键词与铃声。
2. API 36 可用 `adb -s <serial> shell cmd notification post -t KTEST test-tag KTEST`。参数不要包含未正确引用的空格；先确认 shell UID 为 2000，`adb root` 状态下此入口可能失效。
3. 检查 `dumpsys media.player` 中本应用播放器及 `state(5)`（STARTED）、闹钟流 `stream type(4)`；有限次数采用手动续播，不要求 `looping(1)`。系统媒体自带的 `autoLoop` 也不能证明应用设置了无限循环。
4. 检查弹窗关键词、来源、摘要和时间。UI 操作只从当前 UI XML 取得控件 bounds，每次点击重新获取；截图仅用于目检。
5. 点击「已知晓，停止报警」，确认持久化队首消失或推进下一项、历史新增、播放器停止；回到前台或重建进程后不能恢复已经确认的项。
6. 崩溃恢复用 `am crash`，按队列 ID、顺序和新 PID 验证。该命令可能留下系统崩溃弹窗；不要只根据短暂未查询到 PID 判断恢复。

## 最小检查矩阵

| 改动范围 | 必要检查 |
| --- | --- |
| 匹配、播放、确认 | 构建、闭环，同一通知更新、损坏音频、确认后重连 |
| 队列与持久化 | 平板仪器测试及压力脚本；FIFO、聚合、容量拒绝、保存失败、恢复与最终播放器释放 |
| 延时报警 | 固定延时、每日时间、立即覆盖、到期一次转移、队列满时重试、提前检查后重新安排 |
| 铃声选择 | 静音、关键词目标旋转恢复、小屏横屏和大字体滚动到末项 |
| 弹窗与历史 | 更新提示与下载进度不遮挡确认；恢复元信息，历史页刷新、相同时间记录无重复标识 |
| 更新安装 | 完整 APK、截断/损坏、大小或 SHA-256 不匹配、包名/版本/签名不符、下载及安装器失败提示 |
| 监听状态 | 无信号超过启动窗口、断连与心跳过期后的重试及重新授权入口 |

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
powershell -ExecutionPolicy Bypass -File scripts/run-alert-stress.ps1 -AvdName VisionGuard_Tablet_API36
```

仪器测试报告在 `app/build/reports/androidTests/connected/`，压力证据在 `app/build/reports/alert-stress/<run>/`。它们是本机生成产物，不进入版本历史。压力脚本只针对指定模拟器，会重置该模拟器上的 Vigil 测试数据。

精确与非精确闹钟分别验证；电池白名单可能让系统忽略 appops deny，检查前同时核对两者。短观测窗口的到期计时不能作为精度保证。关机后没有开机闹钟重设接收器，服务再次启动前不能承诺按计划响铃。

调试更新接口只对 Debug 生效。较大本地响应使用 `adb reverse tcp:<port> tcp:<port>` 和设备侧 `127.0.0.1`；外部系统安装、Play Protect 和同签名 Release 升级保留给用户验收。

## 真机补充验收

真机使用同签名当前包；发通知前先由用户确认闹钟流静音。分别检查锁屏与其他页面、长时间后台、延时、有限次数、确认持久化、系统铃声与字号旋转；浅色和深色均检查输入、列表、弹窗与应用过滤，不能用模拟器播放器状态代替听感和厂商后台证明。
