# Vigil 项目规则

Vigil 是 Android 通知关键词报警应用。Agent 开始工作前先读本页；具体步骤按下方索引查阅。

## 必守规则

1. **真机静音铁律**：真机测试先确认闹钟流已静音；最小音量不等于无声。用 `dumpsys media.player` 验证播放与停止，不让设备实际出声。
2. **闭环验证**：改动通知匹配、响铃、弹窗或确认停止后，必须按[设备验证手册](docs/testing.md)完成闭环测试；有真机用真机，否则优先高版本模拟器。
3. **关键状态持久化**：报警关键状态必须持久化。服务到 UI 的关键事件须有持久化兜底；`VigilEventBus` 的 replay 例外与时间戳规则见[项目细则](docs/project-rules.md)。
4. **签名与密钥安全**：GitHub CI 从仓库 secrets 取得轮换后的 release 签名密钥；仓库和本地检出没有 `keystore/vigil.keystore` 是正常状态。密钥文件和密码不得入库。
5. **文档同步**：提交、push 或收口前，对齐负责相关事实的文档；[README](README.md) 面向人，本页面向 Agent，[DESIGN](DESIGN.md) 承载视觉设计与实现规范。
6. **视觉检查与虚拟机窗口**：需要用户进行视觉检查时，默认启动有可见窗口的虚拟机；仅在用户不在场或明确要求后台执行时才使用无窗口模式。视觉检查或较长验证链路先让用户选择执行者；详细要求见[设备验证手册](docs/testing.md#环境与边界)与[项目细则](docs/project-rules.md)。
7. **路线图维护**：[路线图](ROADMAP.md)的已完成内容只放一个章节；发现的问题放入当前规划，完成并验证后及时清理。具体维护方式见[路线图维护规则](docs/project-rules.md#路线图维护)。

上述规则的边界、例外和密钥处理方式以[项目安全与协作细则](docs/project-rules.md)为准。

## 工作索引

- [实现与代码索引](docs/architecture.md)：源码分包、核心链路、持久化与构建入口。
- [设计文档](DESIGN.md)：视觉理念、主题 Token、自适应布局与图标规范。
- [设备验证手册](docs/testing.md)：设备选择、闭环流程与最小验证矩阵。
- [平台兼容经验](docs/platform.md)：系统差异、故障排查和已知限制。
- [构建与发布手册](docs/release.md)：签名、CI、tag 与验收。
- [README](README.md)：用户功能与开始使用；[隐私政策](PRIVACY.md)：数据处理。
- [路线图](ROADMAP.md)：当前规划与已完成内容；[商店素材](store/README.md)及[上架清单](store/owner-checklist.md)：商店提交资料。
- [发行说明](release-notes/)：各版本用户可见变更。
