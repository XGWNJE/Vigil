# Vigil守夜人（通知助手）

## 项目简介
Vigil 通知助手是一款 Android 应用程序，旨在监控设备接收到的通知。当通知内容包含用户预设的关键词时，应用会触发一个强提醒（例如自定义铃声和全屏弹窗），以确保用户不会错过重要信息。这个作品是本人第一个软件处女座，本程序的亮点不在于功能如何，而是实践验证 AI 写代码如今到达如什么程度。特别是对于零基础或只懂少量基础语法的人，AI 编程是否能完整开发一款原生android。对于正经程序员来说就是个小玩具，大佬莫要嘲笑。

## 核心功能
- **关键词监控**：实时监听所有应用通知，匹配用户自定义的关键词列表。
- **自定义铃声提醒**：允许用户为匹配到的关键词通知选择特定的提醒铃声。
- **全屏弹窗提醒**：当匹配到关键词时，通过全屏 Intent 启动一个置顶的弹窗界面 (`AlertDialogActivity`)，以最大程度吸引用户注意。
- **备选通知**：在无法使用全屏 Intent 或权限不足的情况下，会尝试发送一个普通的系统通知作为备选提醒。
- **设置界面 (`MainActivity`)**：
    - 管理和编辑监控的关键词列表。
    - 选择和更改提醒铃声。
    - 启用或禁用通知监听服务。
    - 显示当前服务运行状态。
    - 提供服务异常时的恢复尝试机制。
- **权限管理**：在应用内引导用户授予必要的权限，并显示权限状态。
- **环境检测**：检查设备的勿扰模式、静音模式、铃声音量等，并在可能影响提醒效果时向用户发出警告。
- **后台服务 (`MyNotificationListenerService`)**：作为前台服务在后台持续运行，以确保通知监听的可靠性。

## 如何构建和运行
### 环境要求
- Android Studio (最新稳定版推荐)。

### 导入项目
在 Android Studio 中打开项目。

### 构建配置
- 确保 `minSdkVersion` 设置符合您的目标（当前代码中的修复基于 `minSdkVersion` >= 26 的假设）。
- `targetSdkVersion` 和 `compileSdkVersion` 应设置为较新的 API 级别（例如 33 或更高以支持 `POST_NOTIFICATIONS` 权限）。

### 构建运行
1. 连接 Android 设备或启动模拟器。
2. 点击 Android Studio 工具栏中的 "Run 'app'" 按钮。

### 权限授予
应用启动后，会引导您授予必要的权限。请按照提示在系统设置中完成授权。

### 主要组件和技术：
- ** MyNotificationListenerService: 这是应用的核心，一个继承自 NotificationListenerService 的服务。它负责在后台运行，监听新通知，提取通知内容，与用户设定的关键词进行比对，并在匹配成功后触发上述的提醒流程（播放铃声、显示 AlertDialogActivity）。它还管理着前台服务状态，以确保在 Android 较新版本上能够持续运行。
- ** MainActivity: 这是用户交互的主要界面。用户可以在这里：
    - 设置需要监控的关键词（用逗号分隔）。
    * 保存这些设置。
    * 授予应用运行所必需的各项权限（如通知读取权限、发送通知权限、悬浮窗权限、勿扰模式读取权限等）。
    * 启动或停止关键词监听服务。
    * 查看当前服务的运行状态以及一些环境警告（例如，手机是否处于勿扰模式、音量是否过低等，这些由 EnvironmentChecker 提供）。
- ** SharedPreferencesHelper: 工具类，用于将用户的设置（关键词、铃声 URI、服务启用状态）持久化存储在本地。
- ** PermissionUtils: 工具类，集中处理应用所需的各种权限的检查和请求逻辑，包括引导用户到系统设置页面开启特殊权限。
- ** EnvironmentChecker: 工具类，用于检测当前手机的系统环境，如勿扰模式是否开启、各种音量（铃声、通知、媒体）是否合适，并生成警告信息显示在 MainActivity。
- ** build.gradle 配置:  
    * 应用基于 Kotlin 语言开发。
    * 使用了 Android Gradle Plugin，针对 Android 应用打包。
    * compileSdk 和 targetSdk 为 35，minSdk 为 26 (Android 8.0)。
    * 启用了 ViewBinding，方便在代码中访问布局文件中的视图。Compose 相关配置被注释掉了。
    * 依赖库包括了 AndroidX核心库、UI组件（AppCompat, Material Design, ConstraintLayout）、生命周期管理、导航组件以及测试库。
- ** AndroidManifest.xml:  
    * 声明了应用的核心组件：MainActivity, AlertDialogActivity, MyNotificationListenerService。
    * 请求了多项权限，包括 BIND_NOTIFICATION_LISTENER_SERVICE (监听通知的核心)、POST_NOTIFICATIONS (Android 13+ 发送通知，用于全屏提醒)、SYSTEM_ALERT_WINDOW (悬浮窗)、WAKE_LOCK (唤醒锁，确保提醒时设备不休眠)、ACCESS_NOTIFICATION_POLICY (访问勿扰策略) 和 FOREGROUND_SERVICE (运行前台服务)。
- ** 资源文件 (res/):
    * 包含应用的布局 (XML)、字符串 (多为中文)、颜色、主题 (Material 3)、图标等。
    * 界面设计遵循 Material Design 规范。

## 安装与使用步骤
### 安装
按照上述“如何构建和运行”的步骤完成应用的安装。

### 使用
1. 打开应用，进入主界面。
2. 在设置界面中，管理和编辑监控的关键词列表。
3. 选择和更改提醒铃声。
4. 启用通知监听服务。
5. 当设备接收到包含预设关键词的通知时，应用会触发相应的提醒。

## 已知问题与未来改进方向
- **日志中的敏感信息**：`MyNotificationListenerService` 中的 `Log.i` 级别日志仍然会记录匹配到的关键词和来源应用包名。在发布版本中，应移除这些敏感信息的日志记录，或将其级别降至 `Log.d/v` 并通过 Proguard 规则在 release 构建中移除。
- **错误处理**：针对一些边缘情况（如存储空间不足、特定设备兼容性问题）可以增加更完善的错误处理和用户反馈机制。
- **国际化/本地化**：当前应用的 UI 字符串主要为中文。未来可以考虑添加多语言支持。


## 联系方式
如果您有任何问题或建议，请通过以下方式联系我：
- 邮箱：[xgwnje@qq.com]                      
- GitHub Issues：[https://github.com/your-repo/Vigil/issues](https://github.com/your-repo/Vigil/issues)
