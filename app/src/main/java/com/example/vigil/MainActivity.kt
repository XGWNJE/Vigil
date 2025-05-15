// src/main/java/com/example/vigil/MainActivity.kt
package com.example.vigil

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast // Toast 可以保留，它是非 UI 组件
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// 导入 Compose 相关内容
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.* // 导入 Material 2 组件，我们将自定义样式
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource // 导入 stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent // 导入 setContent

// 导入 Navigation Compose
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// 导入自定义主题和目的地
import com.example.vigil.ui.AppDestinations
import com.example.vigil.ui.BottomNavDestinations
import com.example.vigil.ui.monitoring.MonitoringScreen // 导入监控屏幕 Composable
import com.example.vigil.ui.settings.SettingsScreen // 导入设置屏幕 Composable
import com.example.vigil.ui.theme.VigilTheme // 导入自定义主题

// 保留非 UI 相关的导入
import com.example.vigil.LicenseManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // 移除 binding 变量，不再使用 ViewBinding
    // private lateinit var binding: ActivityMainBinding

    // 保留非 UI 相关的依赖对象
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private lateinit var licenseManager: LicenseManager

    // 移除所有与 XML UI 元素直接相关的变量和 Handler/Runnable
    // private var selectedRingtoneUri: Uri? = null
    // private var lastHeartbeatTime: Long = 0
    // private val heartbeatCheckHandler = Handler(Looper.getMainLooper())
    // private val heartbeatCheckRunnable = ...
    // 移除广播接收器，它的逻辑会迁移到 ViewModel 或 Composable 中处理
    // private val serviceStatusReceiver = ...


    companion object {
        private const val TAG = "VigilMainActivity"

        // 移除不再直接在 MainActivity 中使用的 UI 相关常量
        // val PREDEFINED_COMMUNICATION_APPS = ...
        // private const val HEARTBEAT_INTERVAL_MS = ...
        // private const val HEARTBEAT_TOLERANCE_MS = ...
        // private const val HEARTBEAT_CHECK_INTERVAL_MS = ...
        // private const val HEARTBEAT_TIMEOUT_MS = ...

        // 保留服务状态广播 Action，这个是逻辑层面的通信，后续可能通过 ViewModel 或其他方式处理
        const val ACTION_SERVICE_STATUS_UPDATE = "com.example.vigil.SERVICE_STATUS_UPDATE"
        const val EXTRA_SERVICE_CONNECTED = "extra_service_connected"
    }

    // PermissionLauncher 可以保留，它与 Activity 生命周期和权限请求结果处理相关
    internal lateinit var appSettingsLauncher: ActivityResultLauncher<Intent>

    // 移除 RingtonePickerLauncher，这个逻辑会迁移到 Compose UI 或 ViewModel
    // private val ringtonePickerLauncher = ...


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate。")

        // 移除 XML 布局加载
        // binding = ActivityMainBinding.inflate(layoutInflater)
        // setContentView(binding.root)

        // 保留初始化非 UI 相关的依赖
        sharedPreferencesHelper = SharedPreferencesHelper(this)
        licenseManager = LicenseManager()

        // 权限 Launcher 保留
        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "Returned from system settings page.")
            // TODO: 在 Compose 中处理权限结果可能需要 ViewModel 传递或状态回调
            // onResume 会被调用，Compose UI 会 recompose
        }

        // *** 设置 Compose 内容 ***
        setContent {
            // 应用自定义主题
            VigilTheme {
                // 构建整个应用的 Compose UI 结构
                VigilApp()
            }
        }

        // 移除所有直接操作 XML UI 或依赖 XML UI 的初始化代码
        // setupGithubLinkForTitle()
        // ViewCompat.setOnApplyWindowInsetsListener(...)
        // setupUIListeners()
        // loadSettings()
        // handleIntentExtras(intent)
        // LocalBroadcastManager.getInstance(this).registerReceiver(...)
        // startHeartbeatCheck()


        Log.d(TAG, "MainActivity onCreate 完成。")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 可以在这里处理 Intent Extra，然后通过 ViewModel 或状态传递给 Compose UI
        // TODO: 迁移 handleIntentExtras 逻辑到 ViewModel 或 Composable
        // handleIntentExtras(intent)
    }

    // 移除所有直接操作 XML UI 或依赖 XML UI 的方法
    // private fun setupGithubLinkForTitle() { ... }
    // private fun setupUIListeners() { ... }
    // private fun checkAndRequestPermissions() { ... }
    // private fun loadSettings() { ... }
    // private fun loadLicenseStatusAndUpdateUI() { ... }
    // private fun saveSettings() { ... }
    // private fun notifyServiceToUpdateSettings() { ... }
    // private fun updateSelectedRingtoneUI() { ... }
    // private fun updateUI() { ... }
    // private fun setCardInteractive(...) { ... }
    // private fun updateFeatureAvailabilityUI(...) { ... }
    // private fun updateLicenseStatusUI(...) { ... }
    // private fun updateServiceStatusUI(...) { ... }
    // private fun updateEnvironmentWarnings() { ... }
    // private fun updateFilterAppsSummary(...) { ... }
    // private fun startHeartbeatCheck() { ... }
    // private fun stopHeartbeatCheck() { ... }
    // private fun checkServiceHeartbeatStatus() { ... }

    // 保留与 Activity 生命周期相关的 override 方法，但内部逻辑已简化或移除
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume。")
        // Compose UI 会自动 recompose，不需要手动调用 updateUI
        // 权限检查、服务状态、授权状态等逻辑会迁移到 ViewModel 或 Composable 中处理
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity onPause。")
        // 心跳检查等逻辑会迁移到 ViewModel 或 Composable 中控制
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy。")
        // 移除广播接收器解注册，它会迁移到 ViewModel 或 Composable
        // 移除心跳检查停止，它会迁移到 ViewModel 或 Composable
    }

    // 保留非 UI 相关的辅助方法，或者考虑将它们迁移到合适的类中
    private fun isDarkThemeActive(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    // 权限请求结果处理，保留 override 方法，但内部逻辑会迁移到 ViewModel 或 Composable
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")
        // TODO: 将权限结果传递给 ViewModel 或 Composable 进行处理
    }

    // 服务启动/停止和组件启用/禁用方法，可以保留在 Activity 中，由 ViewModel 调用
    // TODO: 这些方法需要根据实际需要修改，以适应从 Compose/ViewModel 调用
    fun startVigilService() {
        // ... 现有逻辑 ...
        Log.i(TAG, "Vigil 通知服务已尝试启动。")
        // TODO: 通知 Compose UI 更新状态 (通过 ViewModel 或状态)
    }

    fun stopVigilService() {
        // ... 现有逻辑 ...
        Log.i(TAG, "Vigil 通知服务已尝试停止。")
        // TODO: 通知 Compose UI 更新状态 (通过 ViewModel 或状态)
    }

    fun setNotificationListenerServiceComponentEnabled(enabled: Boolean) {
        // ... 现有逻辑 ...
        Log.i(TAG, "MyNotificationListenerService 组件状态设置为: ${if (enabled) "启用" else "禁用"}")
        // TODO: 通知 Compose UI 更新状态 (通过 ViewModel 或状态)
    }


    //==============================================================
    // Compose UI 的根 Composable，包含底部导航和导航宿主
    //==============================================================

    @Composable
    fun VigilApp() {
        // rememberNavController() 创建并记住一个 NavController
        val navController = rememberNavController()

        // Scaffold 提供基本的 Material Design 布局结构 (顶部栏, 底部栏, FAB, 内容区域)
        // 虽然我们不完全遵循 Material Design 风格，但 Scaffold 提供了一个方便的结构
        Scaffold(
            bottomBar = {
                // 定义底部导航栏
                BottomNavigationBar(navController = navController)
            }
        ) { innerPadding ->
            // NavHost 用于管理屏幕之间的导航
            NavHost(
                navController = navController,
                startDestination = AppDestinations.Monitoring.route, // 设置默认起始屏幕为监控
                modifier = Modifier
                    .padding(innerPadding) // 应用 Scaffold 提供的内边距，避免内容被底部导航栏遮挡
                    .fillMaxSize() // 填充可用空间
            ) {
                // 定义导航目的地和对应的 Composable 屏幕
                composable(AppDestinations.Monitoring.route) {
                    MonitoringScreen() // 监控屏幕 Composable
                }
                composable(AppDestinations.Settings.route) {
                    SettingsScreen() // 设置屏幕 Composable
                }
            }
        }
    }

    //==============================================================
    // 底部导航栏 Composable
    //==============================================================

    @Composable
    fun BottomNavigationBar(navController: NavHostController) {
        // BottomNavigation 是 Material Design 2 的底部导航栏 Composable
        // 我们将通过 Modifier 和颜色参数来使其看起来不那么 Material Design
        BottomNavigation(
            backgroundColor = MaterialTheme.colors.surface, // 使用主题的表面色作为背景
            contentColor = MaterialTheme.colors.onSurface, // 使用主题的在表面色上的文字/图标颜色
            elevation = 8.dp // 设置一个小的阴影
            // TODO: 根据非 Material Design 风格的需求，可能需要自定义背景、指示器等
        ) {
            // 获取当前导航目的地，用于高亮显示底部导航项
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // 遍历所有底部导航目的地
            BottomNavDestinations.forEach { destination ->
                // BottomNavigationItem 是底部导航栏中的单个项目
                BottomNavigationItem(
                    icon = {
                        Icon(
                            destination.icon, // 使用目的地中定义的图标
                            contentDescription = stringResource(id = destination.titleResId) // 使用目的地中定义的标题资源ID作为 contentDescription
                        )
                    },
                    label = { Text(stringResource(id = destination.titleResId)) }, // 使用目的地中定义的标题资源ID
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true, // 判断当前目的地是否被选中
                    onClick = {
                        // 点击底部导航项时，进行导航
                        navController.navigate(destination.route) {
                            // 避免在栈顶重复创建同一目的地
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // 在重新选择同一项目时，保留状态和子视图
                            launchSingleTop = true
                            // 在重新选择之前选择的项目时，恢复状态
                            restoreState = true
                        }
                    },
                    // TODO: 根据非 Material Design 风格的需求，可能需要自定义选中/未选中颜色、指示器等
                    selectedContentColor = MaterialTheme.colors.primary, // 选中时的颜色
                    unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium) // 未选中时的颜色
                )
            }
        }
    }


    //==============================================================
    // 预览 Composable
    //==============================================================

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        VigilTheme { // 在预览中使用自定义主题
            VigilApp() // 预览整个应用结构
        }
    }
}

// 扩展函数，方便将 ByteArray 转换为十六进制字符串用于调试 - 可以保留
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
