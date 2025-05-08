package com.example.vigil

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * EnvironmentChecker 用于检查设备当前的环境设置，如勿扰模式和音量。
 */
object EnvironmentChecker {

    private const val TAG = "EnvironmentChecker"

    /**
     * 检查勿扰 (DND) 模式是否已开启。
     *
     * @param context 上下文。
     * @return 如果 DND 已开启且应用有权检查，则返回 true；否则返回 false。
     */
    private fun isDndEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.w(TAG, "无权检查DND状态 (isDndEnabled)")
                // 返回 false 表示我们无法确认 DND 是否开启，或者可以返回一个特定状态
                return false // 在 getEnvironmentWarnings 中会单独处理权限缺失的提示
            }
            val filter = notificationManager.currentInterruptionFilter
            // INTERRUPTION_FILTER_ALL 表示所有通知都可通行
            // INTERRUPTION_FILTER_UNKNOWN 通常在服务刚启动或状态未知时
            // 其他状态（PRIORITY, ALARMS, NONE）都表示勿扰模式在某种程度上是激活的
            val isDndActive = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            Log.d(TAG, "勿扰模式检查: filter=$filter, isDndActive=$isDndActive")
            return isDndActive
        }
        Log.d(TAG, "系统版本低于 M，不检查DND (isDndEnabled)")
        return false // API 23 以下，DND 功能不标准或不存在
    }

    /**
     * 检查手机是否处于静音模式 (Ringer Mode Silent)。
     * @param context 上下文。
     * @return 如果是静音模式，返回 true。
     */
    private fun isSilentModeActive(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode
        val isActive = ringerMode == AudioManager.RINGER_MODE_SILENT
        Log.d(TAG, "静音模式检查: ringerMode=$ringerMode, isActive=$isActive")
        return isActive
    }

    /**
     * 检查指定音频流的音量是否未达到最大值。
     * @param context 上下文。
     * @param streamType 音频流类型 (例如 AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION)。
     * @return 如果音量未达到最大值，返回 true。
     */
    private fun isStreamVolumeNotMax(context: Context, streamType: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(streamType)
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val isNotMax = currentVolume < maxVolume
        Log.d(TAG, "音量检查 (Stream: $streamType): 当前=$currentVolume, 最大=$maxVolume, isNotMax=$isNotMax")
        return isNotMax
    }

    /**
     * 检查媒体音量是否过低 (定义为0)。
     *
     * @param context 上下文。
     * @return 如果媒体音量为 0，则返回 true；否则返回 false。
     */
    private fun isMediaVolumeZero(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val isZero = currentVolume == 0
        Log.d(TAG, "媒体音量检查: 当前=$currentVolume, isZero=$isZero")
        return isZero
    }


    /**
     * 获取环境检查的警告信息。
     *
     * @param context 上下文。
     * @return 返回一个包含所有警告的字符串，如果没有警告则返回 R.string.all_clear。
     */
    fun getEnvironmentWarnings(context: Context): String {
        val warnings = mutableListOf<String>()

        // 1. 检查DND权限和状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                warnings.add(context.getString(R.string.info_dnd_permission_needed))
                Log.w(TAG, "需要勿扰权限来进行完整DND检查。")
            } else {
                if (isDndEnabled(context)) {
                    warnings.add(context.getString(R.string.warning_dnd_enabled))
                }
            }
        }

        // 2. 检查静音模式
        if (isSilentModeActive(context)) {
            warnings.add(context.getString(R.string.warning_silent_mode_enabled))
        }

        // 3. 检查铃声音量是否为最大
        if (isStreamVolumeNotMax(context, AudioManager.STREAM_RING)) {
            warnings.add(context.getString(R.string.warning_ringer_volume_not_max))
        }

        // 4. 检查通知音量是否为最大
        if (isStreamVolumeNotMax(context, AudioManager.STREAM_NOTIFICATION)) {
            warnings.add(context.getString(R.string.warning_notification_volume_not_max))
        }

        // 5. 检查媒体音量是否为0 (针对自定义铃声可能使用媒体流的情况)
        if (isMediaVolumeZero(context)) {
            warnings.add(context.getString(R.string.warning_media_volume_low))
        }

        return if (warnings.isEmpty()) {
            context.getString(R.string.all_clear)
        } else {
            warnings.joinToString("\n")
        }
    }
}
