package com.example.vigil

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 铃声库：自定义铃声来源（P2）的文件管理。
 * 文件统一存 filesDir/ringtones/（复制导入而非 persistable URI，避免原文件被删/权限失效导致报警静音）；
 * 展示名元数据在 SharedPreferencesHelper（ringtone_library）。
 *
 * 铃声值约定（默认铃声与关键词映射通用）：
 * - "content://..." → 系统铃声 URI
 * - 其他非空字符串 → 铃声库本地文件绝对路径
 * - null → 系统默认闹钟铃声
 */
object RingtoneLibrary {

    private const val TAG = "RingtoneLibrary"
    private const val DIR_NAME = "ringtones"

    /** 播放解析结果：content URI / 本地文件 / 无（调用方回落系统默认闹钟铃声） */
    sealed class DataSource {
        data class ContentUri(val uri: Uri) : DataSource()
        data class LocalFile(val file: File) : DataSource()
    }

    fun libraryDir(context: Context): File {
        return File(context.filesDir, DIR_NAME).apply { mkdirs() }
    }

    /** 铃声值是否为铃声库本地文件（非 content URI）。 */
    fun isLibraryFile(value: String?): Boolean {
        return value != null && !value.startsWith("content://")
    }

    /**
     * 解析铃声值为可播放的数据源。
     * 本地文件缺失时返回 null 并写日志（调用方回落系统默认闹钟铃声）。
     */
    fun resolve(context: Context, value: String?): DataSource? {
        if (value == null) return null
        if (value.startsWith("content://")) {
            return DataSource.ContentUri(Uri.parse(value))
        }
        val file = File(value)
        if (!file.exists()) {
            Log.w(TAG, "铃声文件缺失: $value")
            VigilLogger.w(context, TAG, "铃声文件缺失，回落系统默认闹钟铃声: $value")
            return null
        }
        return DataSource.LocalFile(file)
    }

    // --- 导入 ---

    /**
     * 从 SAF 导入音频文件：复制到铃声库目录，返回 (fileName, displayName)；失败返回 null。
     * displayName 取源文件名（去扩展名），fileName 冲突时自动追加序号。
     */
    fun importFromUri(context: Context, uri: Uri): Pair<String, String>? {
        val resolver = context.contentResolver
        val rawName = queryDisplayName(context, uri) ?: "imported_audio"
        val dotIndex = rawName.lastIndexOf('.')
        val baseName = (if (dotIndex > 0) rawName.substring(0, dotIndex) else rawName).ifBlank { "imported_audio" }
        val ext = if (dotIndex > 0) rawName.substring(dotIndex) else ".audio"
        val target = uniqueFile(context, sanitize(baseName), ext)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Log.i(TAG, "已导入铃声: ${target.name} (来源: $rawName)")
            VigilLogger.i(context, TAG, "铃声已导入: ${target.name} (来源: $rawName)")
            target.name to baseName
        } catch (e: Exception) {
            Log.e(TAG, "导入铃声失败: $uri", e)
            VigilLogger.e(context, TAG, "导入铃声失败: $uri", e)
            target.delete()
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "查询文件名失败: $uri", e)
            null
        }
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_").take(40).ifBlank { "ringtone" }
    }

    private fun uniqueFile(context: Context, baseName: String, ext: String): File {
        val dir = libraryDir(context)
        var candidate = File(dir, baseName + ext)
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName-$i$ext")
            i++
        }
        return candidate
    }

    // --- 录音 ---

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    /** 是否正在录音。 */
    val isRecording: Boolean get() = recorder != null

    /**
     * 开始录音（m4a/AAC）。调用方需先确保 RECORD_AUDIO 权限已授予。
     * 返回 true 表示已开始。
     */
    fun startRecording(context: Context): Boolean {
        if (recorder != null) {
            Log.w(TAG, "已在录音中，忽略重复开始")
            return false
        }
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(libraryDir(context), "recording_$timeTag.m4a")
        return try {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingFile = file
            Log.i(TAG, "录音开始: ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "录音启动失败", e)
            VigilLogger.e(context, TAG, "录音启动失败", e)
            file.delete()
            recorder = null
            recordingFile = null
            false
        }
    }

    /**
     * 停止录音并保存，返回 (fileName, 默认展示名)；未在录音或停止失败返回 null。
     */
    fun stopRecording(context: Context): Pair<String, String>? {
        val r = recorder ?: return null
        val file = recordingFile
        recorder = null
        recordingFile = null
        return try {
            r.stop()
            r.release()
            if (file != null && file.exists() && file.length() > 0) {
                Log.i(TAG, "录音已保存: ${file.name} (${file.length()} bytes)")
                VigilLogger.i(context, TAG, "录音已保存: ${file.name} (${file.length()} bytes)")
                val timeTag = file.name.removePrefix("recording_").removeSuffix(".m4a")
                file.name to "录音 $timeTag"
            } else {
                Log.w(TAG, "录音文件为空，已丢弃")
                file?.delete()
                null
            }
        } catch (e: Exception) {
            // MediaRecorder.stop() 在开始后立即停止时会抛 RuntimeException（无有效帧）
            Log.e(TAG, "录音停止失败，丢弃文件", e)
            runCatching { r.release() }
            file?.delete()
            null
        }
    }

    /** 取消录音（不保存）。 */
    fun cancelRecording() {
        val r = recorder ?: return
        val file = recordingFile
        recorder = null
        recordingFile = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete()
    }

    // --- 删除 / 重命名 ---

    /** 删除铃声库文件本体（元数据由调用方清理）。 */
    fun deleteFile(context: Context, fileName: String): Boolean {
        val ok = File(libraryDir(context), fileName).delete()
        Log.i(TAG, "删除铃声文件: $fileName -> $ok")
        if (ok) VigilLogger.i(context, TAG, "铃声文件已删除: $fileName")
        return ok
    }

    // --- 试听 ---

    private var previewPlayer: MediaPlayer? = null
    private var previewFileName: String? = null

    /** 正在试听的 fileName；未在试听为 null。 */
    val previewingFileName: String? get() = previewFileName

    /** 试听切换：同一文件再点停止；其他文件切换试听。返回切换后是否正在试听。 */
    fun togglePreview(context: Context, fileName: String): Boolean {
        // 点正在试听的条目 = 停止，不得停了又立刻重启
        val wasPreviewing = previewFileName == fileName
        stopPreview()
        if (wasPreviewing) return false
        val file = File(libraryDir(context), fileName)
        if (!file.exists()) {
            Log.w(TAG, "试听失败，文件缺失: $fileName")
            return false
        }
        return try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
            previewFileName = fileName
            Log.d(TAG, "试听开始: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "试听失败: $fileName", e)
            stopPreview()
            false
        }
    }

    fun stopPreview() {
        previewPlayer?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.reset(); it.release() }
        }
        previewPlayer = null
        previewFileName = null
    }
}
