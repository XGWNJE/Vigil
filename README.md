# <img src="app/src/main/ic_launcher-playstore.png" alt="" width="32" height="32"> Vigil

Android 通知关键词报警应用。设置要关注的词和应用后，通知命中时播放闹钟铃声，并在应用内显示报警弹窗；你可以确认停铃，也可以让它按设定次数自动结束。

[下载 APK](https://github.com/XGWNJE/Vigil/releases) · [使用步骤](#快速开始) · [反馈问题](https://github.com/XGWNJE/Vigil/issues)

当前版本 `v1.19.0` · Android 8.0 及以上 · [MIT 许可证](LICENSE)

适合需要盯住值班告警、交易提醒或特定消息通知的人。报警依赖系统允许 Vigil 持续监听通知；各品牌的后台限制和勿扰设置会影响提醒效果。

## 你会在哪儿用到它

| 场景 | 你设置什么 | 命中后会发生什么 |
| --- | --- | --- |
| 值班告警 | 告警关键词，必要时只选工作应用 | 按设定次数播放铃声，打开 Vigil 可确认停铃 |
| 不想立即打断 | 固定延时或每日指定时刻 | 到期后报警；设置页可查看和取消待触发项 |
| 多条通知接连到来 | 重复提醒间隔 | 不同关键词按顺序处理，同来源同关键词合并提醒 |

## 界面预览

| 监听首页 | 报警弹窗 | 应用过滤 |
| :---: | :---: | :---: |
| <img src="screenshots/main.png" alt="Vigil 监听首页" width="220"> | <img src="screenshots/alert.png" alt="关键词报警弹窗" width="220"> | <img src="screenshots/app-filter.png" alt="应用过滤页" width="220"> |

## 快速开始

1. 在 [GitHub Releases](https://github.com/XGWNJE/Vigil/releases) 下载 APK，安装到 Android 8.0 或更高版本的设备。
2. 打开 Vigil，在设置中添加关键词，并授予系统的**通知使用权**；建议按应用内引导关闭针对 Vigil 的电池优化。
3. 按需选择铃声、播放次数、应用过滤和延时策略；返回首页，点中央圆点开启监听。
4. 通知命中后，打开报警弹窗点「已知晓，停止报警」；若应用没有前置，可点报警通知进入处理。

默认命中即报警。铃声使用独立的闹钟音量，播放次数可设为 1–10 次；可以选系统铃声、内置预设，也可以导入音频或录音。报警结束后可在应用内查看记录。应用会在启动时检查 GitHub 更新，设置页也可手动检查。

## 使用前注意

- **后台与锁屏**：Android 10 及以上可能阻止应用从后台直接弹出窗口；此时点报警通知进入应用。厂商省电策略可能中断监听，请检查应用内的连接状态和电池设置。
- **勿扰模式**：铃声走闹钟音频流，但仍受设备的闹钟音量和勿扰规则控制；Vigil 不会修改系统勿扰设置。
- **延时精度**：Android 12 及以上若未授予「闹钟和提醒」，延时报警使用非精确系统闹钟，可能晚于设定时间。设备关机期间不能响铃，重启后的触发时间不作保证。
- **报警恢复**：当前报警和等待队列保存在本机；进程意外结束后，服务重建时会尝试恢复有效期内的报警。恢复依赖系统重新启动并连接通知监听服务。

通知内容只在设备上用于关键词匹配；诊断日志不写通知正文。更新检查和 APK 下载会访问 GitHub，完整数据说明见[隐私政策](PRIVACY.md)。

## 从源码构建

准备 JDK 17 和 Android SDK 35，然后在仓库根目录运行：

```bash
./gradlew assembleDebug
```

Windows PowerShell 可运行 `./gradlew.bat assembleDebug`。成功后 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；构建失败时先检查 JDK、Android SDK 与 Gradle 的错误输出。发布包需要仓库维护者的签名配置，构建与设备验证细节见 [AGENTS.md](AGENTS.md)。

## 更多文档

- [ROADMAP.md](ROADMAP.md)：已确认的规划与已交付里程碑。
- [PRIVACY.md](PRIVACY.md)：中英双语隐私政策。
- [store/README.md](store/README.md)：商店上架文案和权限用途。
- [release-notes/](release-notes/)：各版本的发行说明。
- [AGENTS.md](AGENTS.md)：项目开发、验证和发布操作规则。

欢迎通过 [GitHub Issues](https://github.com/XGWNJE/Vigil/issues) 反馈问题。本项目采用 [MIT License](LICENSE)。
