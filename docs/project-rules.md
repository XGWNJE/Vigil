# 项目安全与协作细则

本页展开 [AGENTS.md](../AGENTS.md) 的强制规则；设备操作步骤见[设备验证手册](testing.md)。

## 中转站真机验证（常驻职责）

- 若你的系统提示已注入全局协作规则（Claude Code / Codex / OpenCode 会自动注入），其中已含本职责，跳过本节。
- 否则（如 Kimi Code）：开发内容未外部发版、仍在测试阶段，且发现用户在用模拟器/虚拟机调试验证、未连接真机时，主动询问用户是否把构建产物传到中转站（get.xgwnje.cn），让用户自行安装到真机测试；问过才传，不擅自上传。

## 铁律（不可违反）

1. 真机测试不得让设备实际出声：先调最小音量，一律用 `dumpsys media.player` 验证播放/停止，不依赖人耳。
2. 改动报警核心链路（通知匹配 → 响铃 → 弹窗 → 确认停止）后，必须跑完整闭环测试才允许交付（验证设备选择见[设备验证手册](testing.md)：有真机用真机，无真机退模拟器并优先高版本）；不接受纯静态检查结论。
3. 报警状态必须持久化（SharedPreferences），不得只存内存：厂商省电策略（Motorola Device Guard 等）随时可能强杀进程，内存状态 = 报警丢失。
4. GitHub CI 使用 `VIGIL_KEYSTORE_BASE64` 和签名密码 secrets，在临时 runner 上生成 `keystore/vigil.keystore` 并签名。仓库及普通本地检出没有该文件是正常状态，不应据此判断密钥丢失。2026-07 轮换后的签名密钥仍须妥善保管；若维护者另有离线备份，不得删除或丢失。旧密钥曾意外入库且密码明文，已视为泄露，不得再用于签名。签名密码只放 CI secrets；仅在确需本地 release 构建时，才使用 gitignored 的 `keystore.properties` 或环境变量（`VIGIL_STORE_PASSWORD`/`VIGIL_KEY_ALIAS`/`VIGIL_KEY_PASSWORD`）。**任何密钥文件与密码都不得提交进仓库**。
5. `VigilEventBus` 除 `heartbeat` 外均为无 replay 的 SharedFlow，进程重建后事件即丢；任何"服务 → UI"的关键事件都必须有持久化兜底。`heartbeat` 例外：`replay=1` 且 payload 携带发射时刻时间戳（`elapsedRealtime`），收集方按时间戳算年龄，陈旧 replay 不会掩盖服务已死。
6. 每次提交 / push / 收口任务前必须对齐文档：事实变了就同步更新对应文档。README 始终保持简洁，不重要的内容不写进去；不是特别重要但有必要记住的东西，写进 AGENTS.md 或其他专门文档，不堆在 README。
