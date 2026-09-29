# Vigil 项目规则

Vigil 是 Android 通知关键词报警应用。Agent 开始工作前先读本页；具体步骤按下方索引查阅。

## 必守规则

1. 真机测试先调最小音量，用 `dumpsys media.player` 验证播放与停止，不让设备实际出声。
2. 改动通知匹配、响铃、弹窗或确认停止后，必须按[设备验证手册](docs/testing.md)完成闭环测试；有真机用真机，否则优先高版本模拟器。
3. 报警关键状态必须持久化。服务到 UI 的关键事件须有持久化兜底；`VigilEventBus` 的 replay 例外与时间戳规则见[项目细则](docs/project-rules.md)。
4. GitHub CI 从仓库 secrets 取得轮换后的 release 签名密钥；仓库和本地检出没有 `keystore/vigil.keystore` 是正常状态。密钥文件和密码不得入库。
5. 提交、push 或收口前，对齐负责相关事实的文档；README 面向使用者，本页面向 Agent。
6. 视觉检查或较长验证链路先让用户选择执行者；模拟器验证转真机的中转站询问条件见[项目细则](docs/project-rules.md)。

上述规则的边界、例外和密钥处理方式以[项目安全与协作细则](docs/project-rules.md)为准。

## 工作索引

- [实现与代码索引](docs/architecture.md)：核心链路、持久化、UI 和构建入口。
- [设备验证手册](docs/testing.md)：设备选择、闭环流程与最小验证矩阵。
- [平台兼容经验](docs/platform.md)：系统差异、故障排查和已知限制。
- [构建与发布手册](docs/release.md)：签名、CI、tag 与验收。
- [README](README.md)：用户功能与开始使用；[隐私政策](PRIVACY.md)：数据处理。
- [路线图](ROADMAP.md)：已确认计划；[商店素材](store/README.md)及[上架清单](store/owner-checklist.md)：商店提交资料。
- [发行说明](release-notes/)：各版本用户可见变更；[设计资料](design/icons/PHILOSOPHY.md)：图标视觉理念。
