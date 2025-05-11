# Vigil 通知助手

## 项目简介

Vigil 通知助手是一款 Android 应用程序，旨在监控设备接收到的通知。当通知内容包含用户预设的关键词时，应用会触发一个强提醒（例如自定义铃声和全屏弹窗），以确保用户不会错过重要信息。

## 核心功能

* **关键词监控**：实时监听所有应用通知，匹配用户自定义的关键词列表。
* **自定义铃声提醒**：允许用户为匹配到的关键词通知选择特定的提醒铃声。
* **全屏弹窗提醒**：当匹配到关键词时，通过全屏 Intent 启动一个置顶的弹窗界面 (`AlertDialogActivity`)，以最大程度吸引用户注意。
* **备选通知**：在无法使用全屏 Intent 或权限不足的情况下，会尝试发送一个普通的系统通知作为备选提醒。
* **设置界面 (`MainActivity`)**：
    * 管理和编辑监控的关键词列表。
    * 选择和更改提醒铃声。
    * 启用或禁用通知监听服务。
    * 显示当前服务运行状态。
    * 提供服务异常时的恢复尝试机制。
* **权限管理**：在应用内引导用户授予必要的权限，并显示权限状态。
* **环境检测**：检查设备的勿扰模式、静音模式、铃声音量等，并在可能影响提醒效果时向用户发出警告。
* **后台服务 (`MyNotificationListenerService`)**：作为前台服务在后台持续运行，以确保通知监听的可靠性。

## 所需权限

为了实现上述功能，应用需要以下权限：

* `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`：读取设备接收到的通知。
* `android.permission.ACCESS_NOTIFICATION_POLICY`：读取勿扰模式状态。
* `android.permission.SYSTEM_ALERT_WINDOW`：在其他应用上层显示提醒窗口（悬浮窗权限）。
* `android.permission.WAKE_LOCK`：在接收到匹配通知时唤醒设备，确保提醒能够及时发出。
* `android.permission.POST_NOTIFICATIONS`：(Android 13+) 发送通知（包括全屏 Intent 和备选通知）。
* `android.permission.FOREGROUND_SERVICE`：允许服务作为前台服务运行。
* `android.permission.FOREGROUND_SERVICE_DATA_SYNC`：(Android 14+) 前台服务类型声明，适用于数据同步类任务（此处用于维持服务运行）。
* `android.permission.USE_FULL_SCREEN_INTENT`：允许应用发送全屏 Intent 以显示高优先级提醒。

## 主要组件

* **`MainActivity.kt`**: 应用的主界面，负责用户交互、设置管理、权限请求和服务控制。
* **`MyNotificationListenerService.kt`**: 核心的通知监听服务，在后台运行，处理通知的接收、关键词匹配和触发提醒。
* **`AlertDialogActivity.kt`**: 用于显示全屏提醒的界面，确保用户能够注意到关键词匹配事件。
* **`SharedPreferencesHelper.kt`**: 工具类，用于持久化存储应用的配置信息，如关键词列表和选择的铃声 URI。
* **`PermissionUtils.kt`**: 工具类，封装了权限检查和请求的相关逻辑。
* **`EnvironmentChecker.kt`**: 工具类，用于检查设备环境（如勿扰模式、音量设置）是否适合发出提醒。

## 当前开发进度

* **核心功能已实现**：包括关键词匹配、自定义铃声播放、全屏弹窗提醒、备选通知机制。
* **用户界面基本完善**：`MainActivity` 提供了对关键词、铃声、服务开关的配置管理。
* **权限处理流程清晰**：应用内会引导用户授予必要的权限，并在界面上反馈权限状态。
* **服务状态监控与恢复**：UI 会显示服务的实际运行状态，并在服务意外停止时提供“尝试重启服务”的选项。
* **代码健壮性提升**：已根据 IDE 的提示修复了多处警告，包括废弃 API 的使用、不必要的 SDK 版本检查、冗余代码的移除等。
* **日志系统**：调试日志主要使用中文输出，便于开发和问题定位。

## 如何构建和运行

1.  **环境要求**：Android Studio (最新稳定版推荐)。
2.  **导入项目**：在 Android Studio 中打开项目。
3.  **构建配置**：
    * 确保 `minSdkVersion` 设置符合您的目标（当前代码中的修复基于 `minSdkVersion` >= 26 的假设）。
    * `targetSdkVersion` 和 `compileSdkVersion` 应设置为较新的 API 级别（例如 33 或更高以支持 `POST_NOTIFICATIONS` 权限）。
4.  **构建运行**：
    * 连接 Android 设备或启动模拟器。
    * 点击 Android Studio 工具栏中的 "Run 'app'" 按钮。
5.  **权限授予**：应用启动后，会引导您授予必要的权限。请按照提示在系统设置中完成授权。

## 已知问题与未来改进方向

* **日志中的敏感信息**：`MyNotificationListenerService` 中的 `Log.i` 级别日志仍然会记录匹配到的关键词和来源应用包名。在发布版本中，应移除这些敏感信息的日志记录，或将其级别降至 `Log.d/v` 并通过 Proguard 规则在 release 构建中移除。
* **用户体验 (UX)**：可以进一步打磨 UI 设计和交互流程，使其更加直观易用。
* **错误处理**：针对一些边缘情况（如存储空间不足、特定设备兼容性问题）可以增加更完善的错误处理和用户反馈机制。
* **国际化/本地化**：当前应用的 UI 字符串主要为中文。未来可以考虑添加多语言支持。
* **铃声播放焦点管理**：虽然使用了 `USAGE_ALARM`，但在非常复杂的音频场景下，可以进一步细化音频焦点的请求和管理。

