// src/main/java/com/example/vigil/MainActivity.kt
package com.example.vigil

import android.app.AlertDialog
import android.app.Application
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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

    private val monitoringViewModel: MonitoringViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "MainActivity onCreate. Intent Action: ${intent?.action}")
        handleWindowFlagsForAlert(intent)

        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity super.onCreate called.")

        sharedPreferencesHelper = SharedPreferencesHelper(this)

        appSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "Returned from system settings page.")
            // 刷新所有相关权限状态，包括MIUI的（如果适用）
            settingsViewModel.updatePermissionStates()
            monitoringViewModel.updateEnvironmentWarnings()
        }

        // 设置 ViewModel 回调
        monitoringViewModel.startServiceCallback = { hasPermission -> startVigilService(hasPermission) }
        monitoringViewModel.stopServiceCallback = { stopVigilService() }
        monitoringViewModel.restartServiceCallback = { restartService() }
        monitoringViewModel.notifyServiceToUpdateSettingsCallback = { notifyServiceToUpdateSettings() }

        // 新增：为 SettingsViewModel 设置 MIUI 权限请求回调
        settingsViewModel.requestMiuiBackgroundPopupPermissionCallback = {
            PermissionUtils.requestMiuiBackgroundPopupPermission(this)
        }
        // 设置通知服务更新设置的回调
        settingsViewModel.notifyServiceToUpdateSettingsCallback = { notifyServiceToUpdateSettings() }

        setContent {
            VigilTheme {
                VigilApp(
                    monitoringViewModel = this.monitoringViewModel,
                    settingsViewModel = this.settingsViewModel
                )

                val showDialog by this.monitoringViewModel.showKeywordAlertDialog
                val matchedKeyword by this.monitoringViewModel.matchedKeywordForDialog

                if (showDialog) {
                    KeywordAlertDialog(
                        onDismissRequest = { this.monitoringViewModel.onKeywordAlertDialogDismiss() },
                        onConfirm = { this.monitoringViewModel.onKeywordAlertDialogConfirm() },
                        matchedKeyword = matchedKeyword
                    )
                }

                LaunchedEffect(intent) {
                    Log.d(TAG, "LaunchedEffect in setContent. Current intent action: ${this@MainActivity.intent?.action}")
                    handleIntentForAlert(this@MainActivity.intent)
                }
            }
        }
        Log.d(TAG, "MainActivity onCreate 完成。")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "MainActivity onNewIntent. Action: ${intent?.action}")
        setIntent(intent)
        handleWindowFlagsForAlert(intent)
        handleIntentForAlert(intent)
    }

    private fun handleWindowFlagsForAlert(intent: Intent?) {
        if (intent?.action == MyNotificationListenerService.ACTION_SHOW_ALERT_FROM_SERVICE) {
            Log.i(TAG, "MainActivity 检测到 ACTION_SHOW_ALERT_FROM_SERVICE，尝试设置窗口标志。")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            Log.d(TAG, "窗口标志已尝试设置。")
        }
    }

    private fun handleIntentForAlert(intent: Intent?) {
        Log.d(TAG, "handleIntentForAlert called. Intent Action: ${intent?.action}")
        if (intent?.action == MyNotificationListenerService.ACTION_SHOW_ALERT_FROM_SERVICE) {
            val keyword = intent.getStringExtra(MyNotificationListenerService.EXTRA_ALERT_KEYWORD_FROM_SERVICE)
            if (!keyword.isNullOrEmpty()) {
                Log.i(TAG, "MainActivity 从 Intent 中接收到显示提醒的指令，关键词: $keyword")
                monitoringViewModel.triggerShowKeywordAlert(keyword)
                intent.action = null
                intent.removeExtra(MyNotificationListenerService.EXTRA_ALERT_KEYWORD_FROM_SERVICE)
                Log.d(TAG, "Intent action and extra cleared after processing in handleIntentForAlert.")
            } else {
                Log.w(TAG, "MainActivity 收到 ACTION_SHOW_ALERT_FROM_SERVICE 但关键词为空。")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume。")
        settingsViewModel.updatePermissionStates() // 会同时更新MIUI权限（如果适用）
        monitoringViewModel.updateEnvironmentWarnings()
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
            
            // 检查是否是首次启动服务且尚未显示过捐赠提示
            if (!sharedPreferencesHelper.hasShownDonateDialog()) {
                showDonateDialog()
                sharedPreferencesHelper.markDonateDialogShown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Vigil service: ", e)
            Toast.makeText(this, getString(R.string.error_starting_service, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun showDonateDialog() {
        Log.i(TAG, "显示捐赠对话框")
        AlertDialog.Builder(this)
            .setTitle(R.string.donate_dialog_title)
            .setMessage(R.string.donate_dialog_message)
            .setPositiveButton(R.string.donate_button) { _, _ ->
                // 打开捐赠链接
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donate_url)))
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "打开捐赠链接失败", e)
                    Toast.makeText(this, R.string.error_opening_url, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.donate_later) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    fun stopVigilService() {
        val serviceIntent = Intent(this, MyNotificationListenerService::class.java)
        try {
            stopService(serviceIntent)
            Log.i(TAG, "Vigil Notification Service has been attempted to stop.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Vigil service: ", e)
        }
        // 不再禁用通知监听服务组件，这样系统不会回收权限
        // setNotificationListenerServiceComponentEnabled(false)
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
        monitoringViewModel: MonitoringViewModel,
        settingsViewModel: SettingsViewModel
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
                    MonitoringScreen(viewModel = monitoringViewModel)
                }
                composable(AppDestinations.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel)
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
            val context = LocalContext.current
            val app = context.applicationContext as Application
            VigilApp(
                monitoringViewModel = MonitoringViewModel(app),
                settingsViewModel = SettingsViewModel(app)
            )
        }
    }
}
