// src/main/java/com/example/vigil/ui/settings/SettingsViewModel.kt
package com.example.vigil.ui.settings

import android.app.Application
import android.app.AppOpsManager // 新增
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 定义应用信息数据类
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isSelected: Boolean = false  // 改为不可变属性，不要直接修改
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPreferencesHelper = SharedPreferencesHelper(context)

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    // --- 权限状态 ---
    private val _hasNotificationAccess = mutableStateOf(false)
    val hasNotificationAccess: State<Boolean> = _hasNotificationAccess

    private val _hasDndAccess = mutableStateOf(false)
    val hasDndAccess: State<Boolean> = _hasDndAccess

    private val _canDrawOverlays = mutableStateOf(false)
    val canDrawOverlays: State<Boolean> = _canDrawOverlays

    private val _canPostNotifications = mutableStateOf(false)
    val canPostNotifications: State<Boolean> = _canPostNotifications

    // --- 新增：MIUI 相关状态 ---
    private val _isMiuiDevice = mutableStateOf(false)
    val isMiuiDevice: State<Boolean> = _isMiuiDevice

    // 这个状态更多是用于UI提示，实际权限状态可能无法准确获取
    private val _miuiBackgroundPopupPermissionStatus = mutableStateOf(AppOpsManager.MODE_ALLOWED)
    val miuiBackgroundPopupPermissionStatus: State<Int> = _miuiBackgroundPopupPermissionStatus


    // --- 应用过滤 ---
    private val _isAppFilterEnabled = mutableStateOf(sharedPreferencesHelper.getFilterAppsEnabledState())
    val isAppFilterEnabled: State<Boolean> = _isAppFilterEnabled
    
    // 新增：应用列表相关状态
    private val _isLoadingApps = mutableStateOf(false)
    val isLoadingApps: State<Boolean> = _isLoadingApps
    
    private val _installedApps = mutableStateListOf<AppInfo>()
    val installedApps: List<AppInfo> = _installedApps
    
    private val _showOnlySelectedApps = mutableStateOf(false)
    val showOnlySelectedApps: State<Boolean> = _showOnlySelectedApps
    
    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    init {
        Log.d(TAG, "SettingsViewModel created.")
        _isMiuiDevice.value = PermissionUtils.isMiui() // 初始化时检测是否为 MIUI
        updatePermissionStates()
        loadInstalledApps() // 加载已安装应用列表
    }

    fun updatePermissionStates() {
        _hasNotificationAccess.value = PermissionUtils.isNotificationListenerEnabled(context)
        _hasDndAccess.value = PermissionUtils.isDndAccessGranted(context)
        _canDrawOverlays.value = PermissionUtils.canDrawOverlays(context)
        _canPostNotifications.value = PermissionUtils.canPostNotifications(context)

        if (_isMiuiDevice.value) {
            _miuiBackgroundPopupPermissionStatus.value = PermissionUtils.checkMiuiBackgroundPopupPermissionStatus(context)
            Log.d(TAG, "MIUI Background Popup Permission (heuristic check) status: ${_miuiBackgroundPopupPermissionStatus.value}")
        }

        Log.d(TAG, "Permission states updated: Notification=${_hasNotificationAccess.value}, DND=${_hasDndAccess.value}, Overlay=${_canDrawOverlays.value}, PostNotify=${_canPostNotifications.value}, IsMIUI=${_isMiuiDevice.value}")
    }

    // --- 权限请求回调 (由 Activity 调用) ---
    var requestNotificationListenerPermissionCallback: (() -> Unit)? = null
    var requestDndAccessPermissionCallback: (() -> Unit)? = null
    var requestOverlayPermissionCallback: (() -> Unit)? = null
    var requestPostNotificationsPermissionCallback: (() -> Unit)? = null
    var requestMiuiBackgroundPopupPermissionCallback: (() -> Unit)? = null // 新增 MIUI 回调


    // --- 应用过滤 ---
    fun onAppFilterEnabledChange(enabled: Boolean) {
        _isAppFilterEnabled.value = enabled
        sharedPreferencesHelper.saveFilterAppsEnabledState(enabled)
        Log.i(TAG, "App filter enabled state changed to: $enabled and saved.")
        
        // 保存当前选中的应用列表
        saveFilteredApps()
    }
    
    // 将方法改为 public
    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    
                    // 使用 getInstalledPackages 代替 getInstalledApplications
                    // 并添加 GET_META_DATA 标志以获取更多信息
                    val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledPackages(PackageManager.GET_META_DATA)
                    }
                    
                    // 获取已保存的选中应用列表
                    val selectedPackages = sharedPreferencesHelper.getFilteredAppPackages()
                    Log.d(TAG, "已从设置中加载 ${selectedPackages.size} 个选中的应用")

                    Log.d(TAG, "加载到 ${installedPackages.size} 个已安装应用包")
                    
                    val appInfoList = mutableListOf<AppInfo>()
                    
                    for (packageInfo in installedPackages) {
                        try {
                            val packageName = packageInfo.packageName
                            
                            // 安全处理 applicationInfo 可能为空的情况
                            val appInfo = packageInfo.applicationInfo ?: continue
                            
                            // 安全获取应用名称
                            val appName = try {
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                packageName // 失败时使用包名
                            }
                            
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val isSelected = selectedPackages.contains(packageName)
                            
                            if (isSelected) {
                                Log.d(TAG, "应用已被选中: $appName ($packageName)")
                            }
                            
                            appInfoList.add(AppInfo(packageName, appName, isSystemApp, isSelected))
                        } catch (e: Exception) {
                            Log.e(TAG, "处理应用 ${packageInfo.packageName} 信息时出错", e)
                        }
                    }
                    
                    // 先按类型（用户应用优先），再按名称排序
                    appInfoList.sortedWith(compareBy<AppInfo> { it.isSystemApp }.thenBy { it.appName.lowercase() })
                }
                
                _installedApps.clear()
                _installedApps.addAll(apps)
                Log.d(TAG, "最终加载了 ${apps.size} 个应用, ${apps.count { it.isSelected }} 个已选择")
            } catch (e: Exception) {
                Log.e(TAG, "加载已安装应用列表时出错", e)
            } finally {
                _isLoadingApps.value = false
            }
        }
    }
    
    fun toggleAppSelection(packageName: String) {
        // 创建一个新的列表
        val updatedList = _installedApps.toMutableList()
        val index = updatedList.indexOfFirst { it.packageName == packageName }
        
        if (index >= 0) {
            // 使用 copy 创建新对象，反转选中状态
            val app = updatedList[index]
            updatedList[index] = app.copy(isSelected = !app.isSelected)
            
            // 更新整个列表以确保触发重组
            _installedApps.clear()
            _installedApps.addAll(updatedList)
            
            Log.d(TAG, "应用 ${app.appName} (${app.packageName}) 的选中状态已从 ${app.isSelected} 更改为 ${!app.isSelected}")
            
            // 自动保存选择
            saveFilteredApps()
        }
    }
    
    fun saveFilteredApps() {
        val selectedPackages = _installedApps.filter { it.isSelected }.map { it.packageName }.toSet()
        sharedPreferencesHelper.saveFilteredAppPackages(selectedPackages)
        Log.i(TAG, "已保存 ${selectedPackages.size} 个选中应用到过滤列表")
        
        // 通知服务更新设置
        notifyServiceToUpdateSettingsCallback?.invoke()
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    fun toggleShowOnlySelected() {
        _showOnlySelectedApps.value = !_showOnlySelectedApps.value
    }
    
    // 过滤后的应用列表
    fun getFilteredApps(): List<AppInfo> {
        val query = _searchQuery.value.trim().lowercase()
        return _installedApps.filter { app ->
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                app.appName.lowercase().contains(query) || 
                app.packageName.lowercase().contains(query)
            }
            
            val matchesSelection = if (_showOnlySelectedApps.value) {
                app.isSelected
            } else {
                true
            }
            
            matchesSearch && matchesSelection
        }
    }
    
    // 通知服务更新设置的回调
    var notifyServiceToUpdateSettingsCallback: (() -> Unit)? = null
}
