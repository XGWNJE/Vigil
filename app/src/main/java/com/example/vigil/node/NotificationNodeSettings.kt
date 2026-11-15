package com.example.vigil.node

import android.content.Context
import com.example.vigil.BuildConfig
import java.net.URI

data class NodeConnection(val endpoint: String = "", val channel: String = "", val deviceId: String = "",
                          val name: String = "Vigil 通知节点", val apiKey: String = "")

class NotificationNodeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("relay_credentials", Context.MODE_PRIVATE)
    val timeZone: String?
        get() = prefs.getString("time_zone", null)?.takeIf(::validAlarmTimeZone)
    fun saveTimeZone(value: String): Boolean = validAlarmTimeZone(value) && commit(prefs.edit().putString("time_zone", value))
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { check(commit(prefs.edit().putBoolean("enabled", value))) { "无法保存通知节点开关" } }
    fun read() = NodeConnection(prefs.getString("endpoint", "") ?: "", prefs.getString("channel", "") ?: "",
        prefs.getString("deviceId", "") ?: "", prefs.getString("name", "Vigil 通知节点") ?: "", prefs.getString("apiKey", "") ?: "")
    fun save(value: NodeConnection): Boolean = valid(value) && commit(prefs.edit().putString("endpoint", value.endpoint)
        .putString("channel", value.channel).putString("deviceId", value.deviceId).putString("name", value.name)
        .putString("apiKey", value.apiKey).putBoolean("enabled", false).remove("time_zone"))
    private fun commit(editor: android.content.SharedPreferences.Editor): Boolean {
        val before = prefs.all
        if (editor.commit()) return true
        val restore = prefs.edit().clear()
        before.forEach { (key, value) -> when (value) {
            is String -> restore.putString(key, value)
            is Boolean -> restore.putBoolean(key, value)
        } }
        restore.commit()
        return false
    }
    companion object {
        fun valid(value: NodeConnection): Boolean = runCatching {
            val uri = URI(value.endpoint)
            val secure = uri.scheme == "wss" || (BuildConfig.DEBUG && uri.scheme == "ws" && uri.host in listOf("127.0.0.1", "localhost", "10.0.2.2"))
            secure && uri.host != null && uri.userInfo == null && uri.fragment == null && uri.query == null && uri.path == "/ws" &&
                value.channel.matches(Regex("[A-Za-z0-9._-]{1,64}")) && value.deviceId.matches(Regex("[A-Za-z0-9._-]{1,128}")) &&
                value.name.length in 1..100 && value.apiKey.length in 16..512
        }.getOrDefault(false)
    }
}
