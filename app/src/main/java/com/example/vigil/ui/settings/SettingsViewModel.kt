// src/main/java/com/example/vigil/ui/settings/SettingsViewModel.kt
package com.example.vigil.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.vigil.MainActivity
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.VigilLogger
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

    // --- 关键词和铃声设置 ---
    // 用 mutableStateListOf 替代逗号字符串，支持 Chip 标签式输入
    private val _keywordList = mutableStateListOf<String>()
    val keywordList: List<String> = _keywordList

    private val _selectedRingtoneUri = mutableStateOf<Uri?>(null)
    val selectedRingtoneUri: State<Uri?> = _selectedRingtoneUri

    private val _selectedRingtoneName = mutableStateOf(context.getString(R.string.no_ringtone_selected))
    val selectedRingtoneName: State<String> = _selectedRingtoneName

    // --- 权限状态 ---
    private val _hasNotificationAccess = mutableStateOf(false)
    val hasNotificationAccess: State<Boolean> = _hasNotificationAccess

    private val _isIgnoringBatteryOptimizations = mutableStateOf(false)
    val isIgnoringBatteryOptimizations: State<Boolean> = _isIgnoringBatteryOptimizations


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
        updatePermissionStates()
        loadInstalledApps() // 只在初始化时加载应用列表
        loadSettings() // 加载关键词和铃声设置
    }

    fun updatePermissionStates() {
        _hasNotificationAccess.value = PermissionUtils.isNotificationListenerEnabled(context)
        _isIgnoringBatteryOptimizations.value = PermissionUtils.isIgnoringBatteryOptimizations(context)

        Log.d(TAG, "Permission states updated: Notification=${_hasNotificationAccess.value}, BatteryWhitelist=${_isIgnoringBatteryOptimizations.value}")
    }

    // --- 应用过滤 ---
    fun onAppFilterEnabledChange(enabled: Boolean) {
        _isAppFilterEnabled.value = enabled
        sharedPreferencesHelper.saveFilterAppsEnabledState(enabled)
        Log.i(TAG, "App filter enabled state changed to: $enabled and saved.")
        // 注意：不要在这里调用 saveFilteredApps() —— 应用列表异步加载，
        // 若在加载完成前切换开关会把用户已选应用清空。选中集合由 toggleAppSelection 逐项持久化。
        notifyServiceToUpdateSettingsCallback?.invoke()
    }
    
    // 将方法改为 public
    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = context.packageManager

                    // 用 launcher intent 查询代替 getInstalledPackages：
                    // 无需 QUERY_ALL_PACKAGES（Google Play 对该权限有严格用途限制），
                    // 只列出桌面可见（有启动入口）的应用，正是用户需要过滤的通知来源。
                    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(
                            launcherIntent,
                            PackageManager.ResolveInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(launcherIntent, 0)
                    }

                    // 获取已保存的选中应用列表
                    val selectedPackages = sharedPreferencesHelper.getFilteredAppPackages()
                    Log.d(TAG, "已从设置中加载 ${selectedPackages.size} 个选中的应用")

                    Log.d(TAG, "加载到 ${resolveInfos.size} 个 launcher 应用")

                    val appInfoList = mutableListOf<AppInfo>()

                    for (resolveInfo in resolveInfos) {
                        try {
                            val packageName = resolveInfo.activityInfo.packageName

                            // 同一应用多个 launcher 入口只保留一条
                            if (appInfoList.any { it.packageName == packageName }) continue

                            val appInfo = resolveInfo.activityInfo.applicationInfo
                            
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
                            Log.e(TAG, "处理应用 ${resolveInfo.activityInfo.packageName} 信息时出错", e)
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
        VigilLogger.i(context, TAG, "过滤选择已保存: ${selectedPackages.size} 个应用 ${selectedPackages.joinToString()}")
        
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

    // --- 关键词和铃声相关方法 ---
    private fun loadSettings() {
        _keywordList.clear()
        _keywordList.addAll(sharedPreferencesHelper.getKeywords())
        _selectedRingtoneUri.value = sharedPreferencesHelper.getRingtoneUri()
        updateSelectedRingtoneName()
    }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty() && !_keywordList.contains(trimmed)) {
            _keywordList.add(trimmed)
            sharedPreferencesHelper.saveKeywords(_keywordList.toList())
            notifyServiceToUpdateSettingsCallback?.invoke()
        }
    }

    fun removeKeyword(keyword: String) {
        _keywordList.remove(keyword)
        sharedPreferencesHelper.saveKeywords(_keywordList.toList())
        notifyServiceToUpdateSettingsCallback?.invoke()
    }

    @Deprecated("Use addKeyword/removeKeyword instead")
    fun onKeywordsChange(newKeywords: String) {
        // 保留兼容性：将逗号分隔字符串解析为列表
        _keywordList.clear()
        _keywordList.addAll(newKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun onRingtoneUriSelected(uri: Uri?) {
        _selectedRingtoneUri.value = uri
        updateSelectedRingtoneName()
        // 立即持久化，避免依赖手动 saveSettings()
        sharedPreferencesHelper.saveRingtoneUri(uri)
        // 通知服务重新加载设置（让 Service.loadSettings() 读取新 URI）
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Ringtone URI selected and saved immediately: $uri")
    }

    private fun updateSelectedRingtoneName() {
        _selectedRingtoneName.value = if (_selectedRingtoneUri.value != null) {
            try {
                RingtoneManager.getRingtone(context, _selectedRingtoneUri.value)?.getTitle(context) ?: context.getString(R.string.default_ringtone_name)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting ringtone title: ${_selectedRingtoneUri.value}", e)
                "未知铃声"
            }
        } else {
            context.getString(R.string.no_ringtone_selected)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            // 保存关键词（keywordList 已实时自动保存，这里再确保一次）
            sharedPreferencesHelper.saveKeywords(_keywordList.toList())
            
            // 保存铃声
            sharedPreferencesHelper.saveRingtoneUri(_selectedRingtoneUri.value)
            
            // 保存过滤应用列表
            saveFilteredApps()
            
            // 通知服务更新设置
            try {
                val intent = Intent(MainActivity.ACTION_UPDATE_SERVICE_CONFIG)
                context.sendBroadcast(intent)
                Log.i(TAG, "Broadcast sent to update service configuration")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending broadcast to update service", e)
            }

            Log.i(TAG, "设置已保存: 关键词=${keywordList.size}个, 铃声URI=${_selectedRingtoneUri.value}")
        }
    }
}

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
