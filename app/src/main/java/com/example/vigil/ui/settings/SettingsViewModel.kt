// src/main/java/com/example/vigil/ui/settings/SettingsViewModel.kt
package com.example.vigil.ui.settings

import android.app.Application
import android.app.AppOpsManager // 新增
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vigil.LicenseManager
import com.example.vigil.LicensePayload
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.SharedPreferencesHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPreferencesHelper = SharedPreferencesHelper(context)
    private val licenseManager = LicenseManager()

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

    // --- 授权 ---
    private val _licenseKeyInput = mutableStateOf("")
    val licenseKeyInput: State<String> = _licenseKeyInput

    private val _licenseStatusText = mutableStateOf(context.getString(R.string.license_status_unlicensed))
    val licenseStatusText: State<String> = _licenseStatusText

    private val _isLicensed = mutableStateOf(false)
    val isLicensed: State<Boolean> = _isLicensed


    init {
        Log.d(TAG, "SettingsViewModel created.")
        _isMiuiDevice.value = PermissionUtils.isMiui() // 初始化时检测是否为 MIUI
        loadLicenseStatus()
        updatePermissionStates()
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
    }

    // --- 授权逻辑 ---
    fun onLicenseKeyInputChange(newKey: String) {
        _licenseKeyInput.value = newKey
    }

    private fun loadLicenseStatus() {
        val payload = sharedPreferencesHelper.getLicenseStatus()
        updateLicenseUI(payload)
        _isLicensed.value = payload != null
    }

    fun activateLicense(currentAppId: String) {
        viewModelScope.launch {
            val key = _licenseKeyInput.value.trim()
            if (key.isEmpty()) {
                _licenseStatusText.value = context.getString(R.string.license_key_hint)
                return@launch
            }

            val payload = licenseManager.verifyLicense(key, currentAppId)
            sharedPreferencesHelper.saveLicenseStatus(payload)
            updateLicenseUI(payload)
            _isLicensed.value = payload != null

            if (payload != null) {
                Log.i(TAG, "License activated successfully: ${payload.licenseType}")
                _licenseKeyInput.value = ""
            } else {
                Log.w(TAG, "License activation failed for key: $key")
            }
        }
    }

    private fun updateLicenseUI(payload: LicensePayload?) {
        _licenseStatusText.value = getDetailedLicenseStatusMessage(context, payload, licenseManager, _licenseKeyInput.value.trim())
    }

    private fun getDetailedLicenseStatusMessage(
        context: Context,
        payload: LicensePayload?,
        licenseManager: LicenseManager,
        attemptedKey: String?
    ): String {
        if (payload != null) {
            if (payload.expiresAt != null && (payload.expiresAt * 1000 < System.currentTimeMillis())) {
                sharedPreferencesHelper.saveLicenseStatus(null)
                return context.getString(R.string.license_status_expired)
            }

            return when (payload.licenseType.lowercase(Locale.getDefault())) {
                "premium" -> {
                    if (payload.expiresAt != null) {
                        val expiryDate = Date(payload.expiresAt * 1000)
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        context.getString(R.string.license_status_valid_premium_expiry_format, dateFormat.format(expiryDate))
                    } else {
                        context.getString(R.string.license_status_valid_premium)
                    }
                }
                else -> context.getString(R.string.license_status_invalid)
            }
        } else {
            if (!attemptedKey.isNullOrBlank()) {
                val decodedParts = licenseManager.decodeLicenseKey(attemptedKey)
                if (decodedParts == null) {
                    return context.getString(R.string.license_parsing_error)
                }
                return context.getString(R.string.license_status_invalid)
            }
            return context.getString(R.string.license_status_unlicensed)
        }
    }
}
