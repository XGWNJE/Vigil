<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" alt="Vigil 图标" height="96">

# Vigil

Android 通知关键词报警应用。添加关键词后，匹配的通知会触发闹钟铃声；打开应用即可确认停铃。

[快速开始](#快速开始) · [能做什么](#能做什么) · [界面预览](#界面预览) · [使用前注意](#使用前注意) · [文档索引](#文档索引)

[![最新发行版](https://img.shields.io/github/v/release/XGWNJE/Vigil?label=version&color=E4FF54)](https://github.com/XGWNJE/Vigil/releases/latest) [![Android 8.0 及以上](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#快速开始) [![MIT 许可证](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

</div>

## 快速开始

1. 从[最新发行版](https://github.com/XGWNJE/Vigil/releases/latest)下载 APK，安装到 Android 8.0 或更高版本的设备。
2. 打开应用，从右上角齿轮或首页底部上滑进入设置，添加关键词并授予通知使用权。Android 13 及以上还应允许发送通知。
3. 按应用引导检查电池优化与后台运行设置，返回首页，点中央圆点开启监听，并确认显示「监听中」。
4. 报警时在应用内点「已知晓，停止报警」。若应用在后台，可点报警通知进入处理。

## 能做什么

- 按关键词和来源应用筛选通知；给不同关键词设置铃声与播放次数（1–10 次）。
- 选择立即报警、固定延时或每日定点；在设置中查看或取消待触发项。
- 让不同关键词的报警排队处理，配置重复提醒间隔，并查看报警记录。
- 导入或录制铃声、导出诊断日志，并从 GitHub 检查应用更新。

## 界面预览

| 监听首页 | 报警弹窗 | 应用过滤 |
| :---: | :---: | :---: |
| <img src="screenshots/main.png" alt="Vigil 监听首页" width="220"> | <img src="screenshots/alert.png" alt="关键词报警弹窗" width="220"> | <img src="screenshots/app-filter.png" alt="应用过滤页" width="220"> |

## 使用前注意

- 厂商省电策略可能中断监听；闹钟音量和系统勿扰规则会影响响铃。Vigil 不会修改系统勿扰设置。
- Android 10 及以上可能阻止应用从后台直接弹窗。点报警通知或手动打开应用即可处理。
- Android 12 及以上未授予「闹钟和提醒」时，延时报警可能晚于设定时间；设备关机期间不会响铃，重启后不保证按原计划准点触发。
- 关键词匹配在设备本地进行，诊断日志不写通知正文；更新检查和下载会访问 GitHub。详见[隐私政策](PRIVACY.md)。

## 文档索引

- [源码构建与发布](docs/release.md)：构建命令、签名和 CI 发版。
- [路线图](ROADMAP.md)：已确认的规划与完成记录；[发行说明](release-notes/)：各版本的用户可见变化。
- [项目规则](AGENTS.md)、[实现索引](docs/architecture.md)、[设备验证](docs/testing.md)与[平台兼容](docs/platform.md)：供开发维护时查阅。
- [商店素材](store/README.md)与[上架清单](store/owner-checklist.md)：应用商店资料。

欢迎通过 [GitHub Issues](https://github.com/XGWNJE/Vigil/issues) 反馈问题。代码采用 [MIT License](LICENSE)。
