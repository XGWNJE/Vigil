// src/main/java/com/example/vigil/MainActivity.kt
package com.example.vigil

import android.app.Application // 保留以备 SettingsViewModelFactory 和 DefaultPreview 使用
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // 导入 by viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider // 如果需要自定义工厂
// import androidx.lifecycle.viewmodel.compose.viewModel // 不再直接在 setContent 中使用
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vigil.ui.AppDestinations
import com.example.vigil.ui.BottomNavDestinations
import com.example.vigil.ui.dialogs.KeywordAlertDialog
import com.example.vigil.ui.monitoring.MonitoringScreen
import com.example.vigil.ui.monitoring.MonitoringViewModel
import com.example.vigil.ui.settings.SettingsScreen
import com.example.vigil.ui.settings.SettingsViewModel
import com.example.vigil.ui.settings.SettingsViewModelFactory
import com.example.vigil.ui.theme.VigilTheme

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    companion object {
        private const val TAG = "VigilMainActivity"
        const val ACTION_SERVICE_STATUS_UPDATE = "com.example.vigil.SERVICE_STATUS_UPDATE"
        const val EXTRA_SERVICE_CONNECTED = "extra_service_connected"
    }

    internal lateinit var appSettingsLauncher: ActivityResultLauncher<Intent>

    // 使用 by viewModels() 委托来获取 ViewModel 实例
    // 假设 MonitoringViewModel 是 AndroidViewModel 或者有无参构造函数
    // 如果 MonitoringViewModel 需要 Application 但不是 AndroidViewModel,
    // 你需要为它创建一个类似 SettingsViewModelFactory 的工厂
    private val monitoringViewModel: MonitoringViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate。")

        sharedPreferencesHelper = SharedPreferencesHelper(this)

        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "Returned from system settings page.")
            // ViewModel 状态更新现在应该通过它们自己的机制（例如，在 onResume 中或通过观察系统事件）处理
            // SettingsViewModel 会在 onResume 中更新其权限状态
        }

        // 设置 ViewModel 回调
        monitoringViewModel.startServiceCallback = { hasPermission -> startVigilService(hasPermission) }
        monitoringViewModel.stopServiceCallback = { stopVigilService() }
        monitoringViewModel.restartServiceCallback = { restartService() }
        monitoringViewModel.notifyServiceToUpdateSettingsCallback = { notifyServiceToUpdateSettings() }

        setContent {
            VigilTheme {
                // 直接使用 Activity 级别的 ViewModel 实例
                VigilApp(
                    monitoringViewModel = this.monitoringViewModel,
                    settingsViewModel = this.settingsViewModel
                )

                // KeywordAlertDialog 逻辑保持不变，因为它依赖于 ViewModel 的状态
                val showDialog by this.monitoringViewModel.showKeywordAlertDialog
                val matchedKeyword by this.monitoringViewModel.matchedKeywordForDialog

                if (showDialog) {
                    KeywordAlertDialog(
                        onDismissRequest = { this.monitoringViewModel.onKeywordAlertDialogDismiss() },
                        onConfirm = { this.monitoringViewModel.onKeywordAlertDialogConfirm() },
                        matchedKeyword = matchedKeyword
                    )
                }
            }
        }
        Log.d(TAG, "MainActivity onCreate 完成。")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume。")
        // 直接使用 Activity 级别的 ViewModel 实例更新权限状态
        try {
            settingsViewModel.updatePermissionStates()
        } catch (e: Exception) {
            // 虽然我们移除了 setContent 中的 try-catch, 但这里的业务逻辑错误处理可以保留
            Log.e(TAG, "Error updating permission states in onResume", e)
        }
    }

    fun startVigilService(hasPermission: Boolean) {
        if (!hasPermission) {
            Toast.makeText(this, R.string.permission_notification_access_missing, Toast.LENGTH_SHORT).show()
            return
        }
        setNotificationListenerServiceComponentEnabled(true)
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.i(TAG, "Vigil Notification Service has been attempted to start.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Vigil service: ", e)
            Toast.makeText(this, getString(R.string.error_starting_service, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil Notification Service has been attempted to stop.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Vigil service: ", e)
        }
        setNotificationListenerServiceComponentEnabled(false)
    }

    fun setNotificationListenerServiceComponentEnabled(enabled: Boolean) {
        val componentName = ComponentName(this, MyNotificationListenerService::class.java)
        val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            if (packageManager.getComponentEnabledSetting(componentName) != newState) {
                packageManager.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
                Log.i(TAG, "MyNotificationListenerService component state set to: ${if (enabled) "enabled" else "disabled"}")
            } else {
                Log.d(TAG, "MyNotificationListenerService component is already in the target state: ${if (enabled) "enabled" else "disabled"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting service component state: ", e)
        }
    }

    fun restartService() {
        Log.i(TAG, "MainActivity received restart service request.")
        setNotificationListenerServiceComponentEnabled(false)
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Re-enabling service component...")
            setNotificationListenerServiceComponentEnabled(true)
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Attempting to start service after re-enabling component...")
                // 使用 Activity 级别的 monitoringViewModel 实例
                if (this.monitoringViewModel.serviceEnabled.value && PermissionUtils.isNotificationListenerEnabled(this)) {
                    startVigilService(true)
                }
            }, 700)
        }, 300)
    }

    private fun notifyServiceToUpdateSettings() {
        if (sharedPreferencesHelper.getServiceEnabledState() && PermissionUtils.isNotificationListenerEnabled(this)) {
            val intent = Intent(this, MyNotificationListenerService::class.java).apply {
                action = MyNotificationListenerService.ACTION_UPDATE_SETTINGS
            }
            try {
                ContextCompat.startForegroundService(this, intent)
                Log.d(TAG, "Sent ACTION_UPDATE_SETTINGS to service.")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service to update settings: ", e)
            }
        } else {
            Log.d(TAG, "Conditions not met to notify service to update settings (switch state or permission).")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun VigilApp(
        monitoringViewModel: MonitoringViewModel, // 参数类型保持不变
        settingsViewModel: SettingsViewModel   // 参数类型保持不变
    ) {
        val navController = rememberNavController()
        Scaffold(
            bottomBar = {
                BottomNavigationBar(navController = navController)
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestinations.Monitoring.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                composable(AppDestinations.Monitoring.route) {
                    MonitoringScreen(viewModel = monitoringViewModel) // 传递 ViewModel
                }
                composable(AppDestinations.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel) // 传递 ViewModel
                }
            }
        }
    }

    @Composable
    fun BottomNavigationBar(navController: NavHostController) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        NavigationBar {
            BottomNavDestinations.forEach { destination ->
                NavigationBarItem(
                    icon = { Icon(destination.icon, contentDescription = stringResource(id = destination.titleResId)) },
                    label = { Text(stringResource(id = destination.titleResId)) },
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        VigilTheme {
            // Preview 环境下，ViewModel 需要手动实例化。
            // 确保这里的实例化方式与实际应用逻辑（如 Application 依赖）兼容。
            val context = LocalContext.current
            val app = context.applicationContext as Application
            VigilApp(
                monitoringViewModel = MonitoringViewModel(app), // 或者使用 Preview 可接受的构造方式
                settingsViewModel = SettingsViewModel(app)   // 或者使用 Preview 可接受的构造方式
            )
        }
    }
}
