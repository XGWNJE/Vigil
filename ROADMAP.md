# Vigil 开发路线图

> 2026-07-30 owner 规划会结论。当前版本 v1.8.3 (versionCode 14)。

## P0 — 设置入口与权限引导重构（✅ 已完成 v1.9.0，2026-07-30 Motorola 真机验证通过）

### 1. 菜单入口优化：增加上滑手势

- 现状：设置 Sheet 只能从顶栏齿轮打开（`MainScreen.kt:211`），不符合底部 Sheet 样式"从下往上拉出"的直觉。
- 改动：主屏空白区域支持**上滑手势**打开同一个设置 Sheet，齿轮入口保留。
- 实现要点：
  - 主屏根容器加 `pointerInput` 垂直拖拽检测，位移 + 速度双阈值触发，避免误触。
  - 不得与涟漪背景、`KeywordAlertDialog` 的触摸事件冲突。
  - 底部可加一条发丝线把手 + 首次启动「上滑打开设置」一次性提示（SharedPreferences 记已提示标记），贴合「一线」主题。
- 验证（纯 UI）：构建 + 截图 + `adb input swipe` 模拟上滑验证 Sheet 弹出、下滑/空白点击关闭。

### 2. 权限项分级重构 + 新增「锁定任务卡片」引导

owner 梳理的分级（按用户是否需要操作、缺失后果排序）：

| 级别 | 项目 | 缺失后果 | 状态可检测 |
|---|---|---|---|
| 必需 | 通知使用权 | 功能不存在 | ✅ |
| 推荐 | 电池白名单 | 后台检测不稳定（Device Guard 类强杀） | ✅ `isIgnoringBatteryOptimizations` |
| 推荐 | **锁定任务卡片**（新增） | 用户划掉任务卡片后进程死亡、无法监听；无 API，纯图文引导 | ❌ |
| 可选 | 自启动管理 | 被杀后无法自动拉起（配合锁卡片才完整） | ❌ |
| 可选 | 后台运行/省电策略 | 部分 ROM 的后台限制 | ❌ |

- UI：设置 Sheet 内权限项按**必需 / 推荐 / 可选**三组重排，组间发丝线分隔 + 小字组标；必需项未开启保持琥珀警示。
- 新增「锁定任务卡片」行：点击弹 PermissionGuideDialog，图文说明主流 ROM 操作路径（多任务界面长按/下拉卡片 → 锁定）；无状态可检测，只做一次性提示 + 「不再提示」。
- 自启动/后台运行两行无法检测状态，文案标注「无法自动检测，设置后请自行确认」。

### 3. 电池白名单 vs 后台运行入口同质化：调研结论 = 不合并

owner 真机（HyperOS）观察到两个入口进入同一区域，调研结论：

- **原生 Android**：`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 弹系统对话框直接加白名单；`ACTION_APPLICATION_DETAILS_SETTINGS`（应用详情 → 省电策略）是另一个区域。二者不同质。
- **小米 MIUI/HyperOS**：小米把电池优化请求重定向到自家「省电策略」页，与应用详情里的后台耗电管理汇合——这是真机上「两个入口进同一区域」的原因；owner 经验「白名单设成后后台即相对稳定」也佐证二者在 HyperOS 殊途同归。
- **其他 ROM**：华为/OPPO/vivo 的 OEM 后台限制**独立于**标准电池白名单存在，两者是不同开关（参考 [OEM restrictions](https://ivangonzalezg-react-native-background-guardian.mintlify.app/concepts/oem-restrictions)）。

决策：**保留两个入口，不合并**。理由：① 白名单状态可程序化检测（能显示已授权/未授权），后台运行不可检测，合并会丢掉可检测状态；② 其他 ROM 上二者不同质，合并损害兼容性。优化仅限：两行归入同一「后台保活」分组，文案互相指引（如「小米设备设置一项即可」）。

## P1 — 关键词级铃声（目标 v1.10.0）

每个关键词可绑定独立铃声，多监听类型时用户凭铃声分辨。

- 数据模型：`SharedPreferencesHelper` 新增 keyword → ringtoneUri 映射持久化（JSON 字符串，新 prefs key）；无映射的关键词回落全局默认铃声。
- 播放链路：命中关键词后查映射取铃声；`playRingtoneLooping` 支持按关键词选 URI。**注意铁律 3**：pending 报警持久化需同时记录铃声 URI，进程重建恢复时播放同一铃声。
- UI：关键词 chip 支持点按/长按进入铃声选择（复用系统 `RingtoneManager` picker）；设置 Sheet「铃声」行改为「默认铃声」。
- 属**报警核心链路**改动 → 铁律 2：完整闭环测试（配置两个关键词不同铃声，分别触发 → `dumpsys media.player` 验证 → 弹窗确认停止；加 `am crash` 恢复场景验证铃声不丢）。

## P2 — 自定义铃声来源（目标 v1.11.0）

在 P1 铃声选择入口上扩展为「铃声库」，三种来源：

1. **系统铃声**：现状的 `RingtoneManager.ACTION_RINGTONE_PICKER`，保留。
2. **导入音频文件**：SAF `ACTION_OPEN_DOCUMENT`（`audio/*`），**复制**到 `filesDir/ringtones/`（不依赖 persistable URI 权限，避免原文件被删/权限失效导致报警静音）；列表支持命名、删除、试听。
3. **录音**：`RECORD_AUDIO` 运行时权限 + `MediaRecorder`（m4a/AAC），保存到同一目录；权限拒绝时按 PermissionGuideDialog 模式引导。

- 播放统一：本地文件直接走现有 MediaPlayer 链路（`USAGE_ALARM` 不变）；铃声文件缺失时回落系统默认闹钟铃声并写 VigilLogger 日志。
- 映射兼容：P1 的 keyword→ringtone 映射值需同时支持系统 URI 与本地文件路径两种形式。
- 验证：闭环（自定义文件/录音作为报警铃声）+ 麦克风权限流程 + 文件删除后的回落行为。

---

## 版本节奏建议

| 阶段 | versionName / versionCode | 验证矩阵档位 |
|---|---|---|
| P0 | v1.9.0 / 15 | 纯 UI + Manifest/权限流程 |
| P1 | v1.10.0 / 16 | 报警核心链路完整闭环 + `am crash` 恢复 |
| P2 | v1.11.0 / 17 | 完整闭环 + 权限流程 |

P1、P2 可合并发一个版本，但实施顺序不变（铃声库依赖关键词级铃声的映射结构）。
