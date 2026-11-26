# Vigil 设计文档

## 设计理念（给人看）

Vigil 采用 **Obsidian Sentinel（静默暗夜哨兵）** 视觉语言，围绕「**秩序、自适应、不说明**」三个目标构建：

- **秩序**：窄屏用 3 格极简底栏（监控 / 规则 / 记录），宽屏自动切换左侧极简图标轨；界面不再堆砌零散按钮与长篇解释文案。
- **自适应**：按容器实际空间平滑切换布局——宽屏自然展开为左右双栏（左发光守望环，右最近报警），窄屏保持舒适的单栏纵向排布；原生跟随系统深色 / 浅色外观。
- **不说明**：主屏居中的发光守望环直接承担状态表达与启停主开关；下方紧凑数字胶囊（词数 / 铃声 / 循环次数 / 延时）点击即调。
- **应用外独立浮窗告警**：关键词命中后直接在当前使用中的其他应用上方弹出独立系统浮窗（无需切入 Vigil 主程序），点「已知晓，停止报警」即刻停铃关闭；完整支持顶部悬浮卡、居中强提醒、宽屏角落浮窗三种形态及自动适配。

---

## 实现规范与 Token（给 Agent 看）

### 颜色与主题（`ui/theme/Theme.kt` · 暖黑石墨 + 琥珀金）

| 主题模式 | 背景 (`background`) | 浮层 (`surface`) | 主信号 (`primary`) | 警戒红 (`error`) | 主文字 (`onSurface`) |
| --- | --- | --- | --- | --- | --- |
| 深色（核心） | `#121110` | `#1C1A17` | `#F5A524` | `#E5484D` | `#F5F2EB` |
| 浅色（暖纸白） | `#F6F4EF` | `#FFFFFF` | `#C87A08` | `#D9383E` | `#1C1917` |

### 自适应与导航规则

- **布局阈值**：按容器实际容纳能力切换——宽度 `>= 660 dp` 时展开极简左侧图标轨 + 左右双栏主屏；窄于此宽度保持单栏 + 3 格底栏（`监控 / 规则 / 记录`）。
- **守望环（`SentinelHeroRing`）**：运行中呈电青蓝发光脉冲，停止呈克制灰环，断连/未授权呈警戒红环；点按环体直接启停监听。
- **零说明文案**：主屏与设置行仅保留短标签、数值、图标和状态徽标，不添加操作说明副标题。

### 应用外系统浮窗告警（`alert/AlertOverlayManager.kt`）

- **技术机制**：`SYSTEM_ALERT_WINDOW` + `WindowManager`（`TYPE_APPLICATION_OVERLAY`），由 `MyNotificationListenerService` 直接弹出与销毁，不强行拉起 `MainActivity`。
- **四种样式模式**（`OverlayAlertStyle` 持久化在 `SharedPreferencesHelper`）：
  - `AUTO`（默认）：屏幕宽度 `>= 600 dp` 用 `CORNER_COMPACT`，否则用 `TOP_BANNER`。
  - `TOP_BANNER`：顶部悬浮警戒卡，不遮挡下半屏。
  - `CENTER_CARD`：居中强提醒浮窗，高对比警戒红脉冲边框。
  - `CORNER_COMPACT`：右上角紧凑浮窗，适合平板/横屏办公不打断工作流。
- **权限与降级兜底**：未授予悬浮窗权限时，主屏与设置页提供一键引导入口；报警触发时自动降级回高优先级通知 + 应用内 `KeywordAlertDialog` 兜底，确保绝不漏报。

### 启动图标与视觉资产

- **定稿资产**：`app/src/main/res/mipmap-*/ic_launcher*.webp`、[ic_launcher-playstore.png](app/src/main/ic_launcher-playstore.png)、[vigil_icon_final.png](design/icons/vigil_icon_final.png)。
- **约束**：启动图标为已定稿位图资产，保留约 75% 内容宽度和至少 15% 安全边距，禁止程序重绘。
