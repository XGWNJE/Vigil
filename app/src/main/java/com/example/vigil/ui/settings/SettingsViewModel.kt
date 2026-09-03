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
import com.example.vigil.RingtoneLibrary
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.AlertRecord
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

// 应用过滤列表排序：已勾选置顶 → 用户应用优先 → 名称
private val appListComparator =
    compareByDescending<AppInfo> { it.isSelected }
        .thenBy { it.isSystemApp }
        .thenBy { it.appName.lowercase() }

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

    // --- 循环次数（1..10）与报警记录 ---
    private val _defaultLoopCount = mutableStateOf(sharedPreferencesHelper.getDefaultLoopCount())
    val defaultLoopCount: State<Int> = _defaultLoopCount

    private val _keywordCooldownSeconds = mutableStateOf(sharedPreferencesHelper.getKeywordCooldownSeconds())
    val keywordCooldownSeconds: State<Int> = _keywordCooldownSeconds

    // 关键词个性化配置版本号：配置变化时 +1 触发 Compose 重组（chip 弹窗按关键词即时查询）
    private val _keywordConfigVersion = mutableStateOf(0)
    val keywordConfigVersion: State<Int> = _keywordConfigVersion

    private val _alertHistoryVersion = mutableStateOf(0)
    val alertHistoryVersion: State<Int> = _alertHistoryVersion

    // --- 铃声库（自定义铃声来源：导入/录音，fileName to displayName） ---
    private val _ringtoneLibrary = mutableStateListOf<Pair<String, String>>()
    val ringtoneLibrary: List<Pair<String, String>> = _ringtoneLibrary

    // 试听状态版本号（试听开始/停止时 +1 触发重组）
    private val _previewVersion = mutableStateOf(0)
    val previewVersion: State<Int> = _previewVersion

    // 录音状态版本号（录音开始/停止时 +1 触发重组）
    private val _recordingVersion = mutableStateOf(0)
    val recordingVersion: State<Int> = _recordingVersion

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
        // 试听自动结束后 UI 版本号也要刷新（否则「■ 停止」状态卡住）
        RingtoneLibrary.onPreviewStateChanged = { _previewVersion.value++ }
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
                    
                    // 排序：已勾选置顶 → 用户应用优先 → 名称
                    appInfoList.sortedWith(appListComparator)
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

            // 重排：勾选置顶、取消落回（与初始加载同一排序规则）
            updatedList.sortWith(appListComparator)

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
        // 仅 content:// 值回填给系统铃声选择器的 EXISTING_URI；铃声库文件/内置预设不适用
        _selectedRingtoneUri.value = sharedPreferencesHelper.getRingtoneValue()
            ?.takeIf { !RingtoneLibrary.isLibraryFile(it) && !RingtoneLibrary.isPresetValue(it) }
            ?.let { Uri.parse(it) }
        updateSelectedRingtoneName()
        loadRingtoneLibrary()
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
        // 同步清理该关键词的个性化配置，避免残留垃圾映射
        sharedPreferencesHelper.clearKeywordConfig(keyword)
        notifyServiceToUpdateSettingsCallback?.invoke()
    }

    // --- 循环次数与关键词级铃声 ---

    fun onDefaultLoopCountSelected(count: Int) {
        val normalized = count.coerceIn(
            SharedPreferencesHelper.MIN_LOOP_COUNT,
            SharedPreferencesHelper.MAX_LOOP_COUNT
        )
        _defaultLoopCount.value = normalized
        sharedPreferencesHelper.saveDefaultLoopCount(normalized)
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Default loop count saved: $normalized")
    }

    fun onKeywordCooldownSelected(seconds: Int) {
        val normalized = seconds.takeIf { it in setOf(0, 30, 60, 180, 300, 600) } ?: 60
        _keywordCooldownSeconds.value = normalized
        sharedPreferencesHelper.saveKeywordCooldownSeconds(normalized)
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Keyword cooldown saved: $normalized seconds")
    }

    /** 关键词的循环次数覆盖；null = 跟随默认。 */
    fun getKeywordLoopCount(keyword: String): Int? {
        return sharedPreferencesHelper.getKeywordLoopCount(keyword)
    }

    fun onKeywordLoopCountSelected(keyword: String, count: Int?) {
        sharedPreferencesHelper.saveKeywordLoopCount(keyword, count)
        _keywordConfigVersion.value++
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Keyword loop count saved: $keyword -> $count")
    }

    /** 关键词绑定铃声的显示名；未绑定返回 null（UI 显示「默认」）。 */
    fun getKeywordRingtoneName(keyword: String): String? {
        val value = sharedPreferencesHelper.getKeywordRingtoneValue(keyword) ?: return null
        return ringtoneValueDisplayName(value)
    }

    /** 关键词绑定铃声为系统铃声时返回其 URI（选择器 EXISTING_URI 用）；铃声库文件/内置预设返回 null。 */
    fun getKeywordRingtoneUri(keyword: String): Uri? {
        return sharedPreferencesHelper.getKeywordRingtoneValue(keyword)
            ?.takeIf { !RingtoneLibrary.isLibraryFile(it) && !RingtoneLibrary.isPresetValue(it) }
            ?.let { Uri.parse(it) }
    }

    fun onKeywordRingtoneSelected(keyword: String, value: String?) {
        sharedPreferencesHelper.saveKeywordRingtone(keyword, value)
        _keywordConfigVersion.value++
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Keyword ringtone saved: $keyword -> $value")
    }

    // --- 铃声库 ---

    fun loadRingtoneLibrary() {
        val entries = sharedPreferencesHelper.getRingtoneLibraryMap()
            .toList()
            .sortedBy { it.second.lowercase() }
        _ringtoneLibrary.clear()
        _ringtoneLibrary.addAll(entries)
    }

    /** 从 SAF 导入音频到铃声库；成功返回 true（UI Toast 用）。 */
    fun importRingtone(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                RingtoneLibrary.importFromUri(context, uri)
            }
            if (result != null) {
                val (fileName, displayName) = result
                sharedPreferencesHelper.putRingtoneLibraryEntry(fileName, displayName)
                loadRingtoneLibrary()
            }
            onResult(result != null)
        }
    }

    /** 删除铃声库条目：文件本体 + 元数据；被引用的铃声值不清除（播放时自动回落并记日志）。 */
    fun deleteLibraryRingtone(fileName: String) {
        RingtoneLibrary.deleteFile(context, fileName)
        sharedPreferencesHelper.removeRingtoneLibraryEntry(fileName)
        loadRingtoneLibrary()
    }

    /** 重命名（仅改展示名元数据，文件名不变）。 */
    fun renameLibraryRingtone(fileName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        sharedPreferencesHelper.putRingtoneLibraryEntry(fileName, trimmed)
        loadRingtoneLibrary()
    }

    // --- 录音 ---

    val isRecording: Boolean get() = RingtoneLibrary.isRecording

    fun startRecording(): Boolean {
        val ok = RingtoneLibrary.startRecording(context)
        _recordingVersion.value++
        return ok
    }

    /** 停止录音并入库；返回 true 表示成功保存。 */
    fun stopRecording(): Boolean {
        val result = RingtoneLibrary.stopRecording(context)
        _recordingVersion.value++
        if (result != null) {
            val (fileName, displayName) = result
            sharedPreferencesHelper.putRingtoneLibraryEntry(fileName, displayName)
            loadRingtoneLibrary()
            return true
        }
        return false
    }

    // --- 试听 ---

    val previewingFileName: String? get() = RingtoneLibrary.previewingFileName

    fun togglePreview(fileName: String) {
        RingtoneLibrary.togglePreview(context, fileName)
        _previewVersion.value++
    }

    /** 试听内置预设铃声（raw 资源）。 */
    fun togglePresetPreview(preset: RingtoneLibrary.PresetRingtone) {
        RingtoneLibrary.togglePresetPreview(context, preset)
        _previewVersion.value++
    }

    fun stopPreview() {
        RingtoneLibrary.stopPreview()
        _previewVersion.value++
    }

    // --- 报警记录 ---

    fun getAlertHistory(): List<AlertRecord> {
        return sharedPreferencesHelper.getAlertHistory()
    }

    fun clearAlertHistory() {
        sharedPreferencesHelper.clearAlertHistory()
        _alertHistoryVersion.value++
    }

    @Deprecated("Use addKeyword/removeKeyword instead")
    fun onKeywordsChange(newKeywords: String) {
        // 保留兼容性：将逗号分隔字符串解析为列表
        _keywordList.clear()
        _keywordList.addAll(newKeywords.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun onRingtoneUriSelected(uri: Uri?) {
        _selectedRingtoneUri.value = uri
        // 立即持久化，避免依赖手动 saveSettings()（先保存再刷新展示名，否则读到的是旧值）
        sharedPreferencesHelper.saveRingtoneValue(uri?.toString())
        updateSelectedRingtoneName()
        // 通知服务重新加载设置（让 Service.loadSettings() 读取新 URI）
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Ringtone URI selected and saved immediately: $uri")
    }

    /** 选择铃声库文件/内置预设作为默认铃声（value 为文件路径或 android.resource:// URI）。 */
    fun onRingtoneValueSelected(value: String?) {
        _selectedRingtoneUri.value = null
        // 先保存再刷新展示名，否则读到的是旧值（原逻辑会把行内名称卡在旧值）
        sharedPreferencesHelper.saveRingtoneValue(value)
        updateSelectedRingtoneName()
        notifyServiceToUpdateSettingsCallback?.invoke()
        Log.i(TAG, "Ringtone value selected and saved immediately: $value")
    }

    /** 铃声值 → 展示名：内置预设取预设名，铃声库文件取库内命名，content URI 取系统铃声标题。 */
    private fun ringtoneValueDisplayName(value: String): String {
        return when {
            RingtoneLibrary.isPresetValue(value) -> RingtoneLibrary.presetDisplayName(value) ?: "预设铃声"
            RingtoneLibrary.isLibraryFile(value) -> {
                val fileName = java.io.File(value).name
                val libraryName = sharedPreferencesHelper.getRingtoneLibraryMap()[fileName]
                when {
                    libraryName != null -> libraryName
                    // 库条目已删（文件随之删除）：原值已失效，提示会回落，不展示裸路径
                    !java.io.File(value).exists() -> context.getString(R.string.ringtone_file_missing)
                    else -> fileName.substringBeforeLast('.')
                }
            }
            else -> {
                try {
                    RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context)
                        ?: context.getString(R.string.default_ringtone_name)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting ringtone title: $value", e)
                    "未知铃声"
                }
            }
        }
    }

    private fun updateSelectedRingtoneName() {
        val value = sharedPreferencesHelper.getRingtoneValue()
        _selectedRingtoneName.value = if (value != null) {
            ringtoneValueDisplayName(value)
        } else {
            context.getString(R.string.no_ringtone_selected)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            // 保存关键词（keywordList 已实时自动保存，这里再确保一次）
            sharedPreferencesHelper.saveKeywords(_keywordList.toList())

            // 铃声已即时持久化（onRingtoneUriSelected/onRingtoneValueSelected），无需重复保存

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
