# Vigil 开发路线图

> 2026-07-30 owner 规划会结论。当前版本 v1.9.0 (versionCode 15)。

## P0 — 设置入口与权限引导重构（✅ 已完成，交付 v1.9.0，2026-07-30 Motorola 真机验证通过）

**摘要**：设置 Sheet 新增上滑手势入口（顶栏齿轮保留，底部发丝线把手 + 首次使用提示）；权限项按必需/推荐/可选三级重排，新增「锁定任务卡片」图文引导；调研结论「电池白名单与后台运行入口同质化」→ 保留两个入口不合并（原生与国产 ROM 行为不同质，且白名单状态可检测）。实现细节见 git 历史（`3f9e7d8`）。

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
| P1 | v1.10.0 / 16 | 报警核心链路完整闭环 + `am crash` 恢复 |
| P2 | v1.11.0 / 17 | 完整闭环 + 权限流程 |

P1、P2 可合并发一个版本，但实施顺序不变（铃声库依赖关键词级铃声的映射结构）。
