package com.example.vigil.ui.main

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.util.ListenerRecovery
import com.example.vigil.util.PermissionUtils
import com.example.vigil.data.SharedPreferencesHelper
import com.example.vigil.ui.monitoring.ServiceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NotificationDashboard(serviceEnabled: Boolean, serviceState: ServiceState, keywordCount: Int,
    ringtone: String, loops: Int, onToggleLocal: () -> Unit, onRingtone: () -> Unit, onLoops: () -> Unit,
    onHistory: () -> Unit, onLibrary: () -> Unit, settingsContent: @Composable (Boolean) -> Unit) {
    val context = LocalContext.current
    val alarmPrefs = remember { SharedPreferencesHelper(context) }
    var page by rememberSaveable { mutableStateOf("监控") }
    var records by remember { mutableStateOf(alarmPrefs.getAlertHistory()) }
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "alert_history") records = alarmPrefs.getAlertHistory()
        }
        alarmPrefs.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { alarmPrefs.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        val tablet = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (tablet) Column(Modifier.width(190.dp).fillMaxHeight().background(colors.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VIGIL", color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Text("关键词报警", color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(26.dp))
                listOf("监控" to Icons.Outlined.Notifications, "关键词" to Icons.Outlined.Tag, "报警记录" to Icons.Outlined.History,
                    "铃声库" to Icons.Outlined.MusicNote, "设置" to Icons.Outlined.Settings).forEach { (label, icon) ->
                    val action = { when(label) { "报警记录" -> onHistory(); "铃声库" -> onLibrary(); else -> page = label } }
                    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().background(if (page == label) colors.primaryContainer else colors.surface, RoundedCornerShape(6.dp)), colors = ButtonDefaults.textButtonColors(contentColor = if (page == label) colors.primary else colors.onSurface)) {
                        Icon(icon, contentDescription = if (label == "设置") "设置" else null, Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("v${com.example.vigil.BuildConfig.VERSION_NAME}", color = colors.onSurfaceVariant, fontSize = 12.sp)
                Text("跟随系统外观", color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(if (tablet) 28.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(page, color = colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(if (page == "监控") "按关键词监听本机通知，命中时响铃并弹窗确认" else if (page == "关键词") "本机通知的匹配与应用过滤" else "声音、延时与运行权限", color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                    if (!tablet) IconButton(onClick = { page = if (page == "监控") "设置" else "监控" }) { Icon(Icons.Outlined.Settings, "设置") }
                }
                if (page != "监控") {
                    if (!tablet) Row { TextButton(onClick = { page = if (page == "关键词") "设置" else "关键词" }) { Text(if (page == "关键词") "设置" else "关键词") }; TextButton(onHistory) { Text("报警记录") }; TextButton(onLibrary) { Text("铃声库") } }
                    settingsContent(page == "关键词")
                } else {
                    @Composable fun Local() { DashboardPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Notifications, null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("通知监听", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(serviceEnabled, { onToggleLocal() })
                        }
                        Text(when(serviceState) { ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> "监听中"; ServiceState.DISABLED -> "已停止"; ServiceState.NO_PERMISSION -> "未授权"; ServiceState.INITIALIZING -> "连接中"; ServiceState.HEARTBEAT_TIMEOUT -> "心跳超时"; ServiceState.LISTENER_DISCONNECTED -> "重连中"; else -> "异常" }, color = colors.onSurfaceVariant)
                        Text("$keywordCount 个关键词", color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = { page = "关键词" }) { Text("管理关键词与应用过滤") }
                        if (serviceState in listOf(ServiceState.LISTENER_DISCONNECTED, ServiceState.HEARTBEAT_TIMEOUT, ServiceState.NO_PERMISSION)) {
                            if (serviceState != ServiceState.NO_PERMISSION) Text("监听未恢复 · 请重试或重新授权", color = colors.error, fontSize = 13.sp)
                            Row { TextButton(onClick = { ListenerRecovery.startFastRecovery(context) }) { Text("立即重试") }; TextButton(onClick = { (context as? Activity)?.let(PermissionUtils::openNotificationListenerSettings) }) { Text("重新授权") } }
                        }
                    } }
                    @Composable fun Voice() { DashboardPanel {
                        Text("声音策略", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("命中关键词时按默认策略播放报警铃声", color = colors.onSurfaceVariant, fontSize = 13.sp)
                        TextButton(onRingtone, Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("默认铃声", Modifier.weight(1f)); Text(ringtone) }
                        TextButton(onLoops, Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("循环次数", Modifier.weight(1f)); Text("$loops 次") }
                        Text("确认后停止；达到次数自动结束", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    } }
                    if (tablet) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { Box(Modifier.weight(1f)) { Local() }; Box(Modifier.weight(1f)) { Voice() } }
                    else { Local(); Voice() }
                    DashboardPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("最近报警", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onHistory) { Text("查看全部") } }
                        if (records.isEmpty()) Text("暂无报警记录", color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                        records.take(4).forEach { record ->
                            val timeText = remember(record.timestamp) { SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(record.timestamp)) }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            Row { Column(Modifier.weight(1f)) { Text(record.keyword); Text(record.sourceApp ?: "本机通知", color = colors.onSurfaceVariant, fontSize = 13.sp) }; Text(timeText, color = colors.onSurfaceVariant, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DashboardPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}
