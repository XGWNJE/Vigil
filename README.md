# Vigil: 智能通知关键词监控应用

Vigil 是一款 Android 应用程序，旨在通过监控通知内容中的特定关键词，在重要信息出现时提醒用户。即使用户处于勿扰模式或静音模式，该应用也能通过自定义铃声和屏幕提醒确保用户不会错过关键通知。

## 核心功能

* **关键词监控**: 用户可以设置一组关键词，当任何应用的通知内容（包括标题和文本）包含这些关键词时，Vigil 会触发提醒。
* **强化提醒**:
  * **自定义铃声**: 即便在静音或震动模式下，应用也会播放用户选定的铃声以进行提醒。
  * **弹窗提醒**: 匹配到关键词时，应用会显示一个显著的弹窗，突出显示匹配到的关键词，并允许用户确认提醒。
  * **唤醒屏幕与解锁**: 提醒能够点亮屏幕并在锁屏状态下显示，确保提醒的及时性。
* **环境状态检测**: 应用会检测当前设备的设置，如勿扰(DND)模式、静音模式以及各类音量（铃声、通知、媒体），并向用户显示可能影响提醒效果的警告信息。
* **灵活配置**:
  * **关键词管理**: 用户可以方便地添加、修改或删除监控的关键词列表。
  * **铃声选择**: 用户可以从系统铃声中选择用于提醒的铃声。
  * **应用过滤**: 用户可以选择仅监听特定应用的通知，或监听所有应用的通知。
  * **服务启停**: 用户可以随时在应用内启动或停止通知监听服务。
* **权限管理**: 应用内提供引导，帮助用户授予必要的权限，如通知读取权限、勿扰模式控制权限、悬浮窗权限等。针对 MIUI 等特定系统，还会提示用户进行额外的后台弹窗等权限设置。
* **授权验证**: 应用包含授权码验证机制，以管理高级功能的使用。

## 主要组件与技术

* **语言**: Kotlin
* **UI**: Jetpack Compose
* **核心服务**:
  * `MyNotificationListenerService`: 继承自 `NotificationListenerService`，用于捕获和处理系统通知。
  * `MainActivity`: 应用主界面，承载监控、设置等UI，并与服务进行交互。
* **数据存储**: SharedPreferences 用于保存用户设置（关键词、铃声、服务状态、授权信息等）。
* **权限处理**: `PermissionUtils` 辅助检查和请求各类系统权限。
* **环境检查**: `EnvironmentChecker` 用于检测设备环境（勿扰、音量等）。
* **提醒机制**: 通过 `MediaPlayer` 播放铃声，`WindowManager` 和 `Activity` 标志位实现弹窗和屏幕唤醒。

## 所需权限

为了实现其核心功能，Vigil 应用需要以下主要权限：

* `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: 监听和读取通知。
* `android.permission.ACCESS_NOTIFICATION_POLICY`: 检查和可能修改勿扰模式设置。
* `android.permission.SYSTEM_ALERT_WINDOW`: 显示悬浮窗提醒。
* `android.permission.WAKE_LOCK`: 唤醒设备以确保提醒及时。
* `android.permission.POST_NOTIFICATIONS` (Android 13+): 发送通知（用于备选提醒等）。
* `android.permission.FOREGROUND_SERVICE` 和 `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: 运行前台服务以持续监听。
* `android.permission.USE_FULL_SCREEN_INTENT`: 用于显示全屏提醒。

## 简要设置

1.  安装应用后，首先进入**设置**页面。
2.  根据提示授予所有必要的权限，特别是**通知读取权限**、**悬浮窗权限**。对于MIUI等特殊系统，请确保开启**后台弹出界面**等相关权限。
3.  返回**监控**页面。
4.  在“通知设定”卡片中，输入您希望监控的**关键词**（用英文逗号分隔）。
5.  选择一个**提醒铃声**。
6.  点击“保存设置”按钮。
7.  在页面顶部的“启用通知监听服务”开关处，打开开关以启动服务。
8.  （可选）在**设置**页面的“应用过滤设定”中，配置是否仅监听特定通讯应用的通知。
9.  （可选）在**设置**页面的“授权码”部分，输入并激活您的授权码以使用全部功能。
10. 

完成以上步骤后，当设备收到包含您设定关键词的通知时，Vigil 应用将会通过铃声和弹窗进行提醒。请留意“环境检测”部分的提示，确保设备设置不会影响提醒效果。
## 授权码

eyJhcHBJZCI6ImNvbS5leGFtcGxlLnZpZ2lsIiwibGljZW5zZVR5cGUiOiJwcmVtaXVtIiwiaXNzdWVkQXQiOjE3NDczOTQ2MzUsImV4cGlyZXNBdCI6MTc0Nzk1ODQwMH0=.QcVbSxqtKx9Wer2fro+12ZQ4NoRIRtDnoWqHnvRMo1c=
