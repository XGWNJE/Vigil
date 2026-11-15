package com.example.vigil.ui.main

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vigil.ListenerRecovery
import com.example.vigil.PermissionUtils
import com.example.vigil.SharedPreferencesHelper
import com.example.vigil.node.*
import com.example.vigil.ui.monitoring.ServiceState
import com.example.vigil.ui.rememberAlarmTimeZone

@Composable
internal fun NotificationDashboard(serviceEnabled: Boolean, serviceState: ServiceState, keywordCount: Int,
    ringtone: String, loops: Int, onToggleLocal: () -> Unit, onRingtone: () -> Unit, onLoops: () -> Unit,
    onHistory: () -> Unit, onLibrary: () -> Unit, settingsContent: @Composable (Boolean) -> Unit) {
    val context = LocalContext.current
    val settings = remember { NotificationNodeSettings(context) }
    val alarmPrefs = remember { SharedPreferencesHelper(context) }
    val node by NotificationNodeService.state.collectAsState()
    val timeZone = rememberAlarmTimeZone()
    var nodeEnabled by remember { mutableStateOf(settings.enabled) }
    var connection by remember { mutableStateOf(settings.read()) }
    var editConnection by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("接警") }
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
                Text("通知节点", color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(26.dp))
                listOf("接警" to Icons.Outlined.Notifications, "关键词" to Icons.Outlined.Tag, "报警记录" to Icons.Outlined.History,
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
                        Text(if (page == "接警") "接收统一服务报警，也可监听本机通知" else if (page == "关键词") "本机通知的匹配与应用过滤" else "声音、延时与运行权限", color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                    if (!tablet) IconButton(onClick = { page = if (page == "接警") "设置" else "接警" }) { Icon(Icons.Outlined.Settings, "设置") }
                }
                if (page != "接警") {
                    if (!tablet) Row { TextButton(onClick = { page = if (page == "关键词") "设置" else "关键词" }) { Text(if (page == "关键词") "设置" else "关键词") }; TextButton(onHistory) { Text("报警记录") }; TextButton(onLibrary) { Text("铃声库") } }
                    settingsContent(page == "关键词")
                } else {
                    DashboardPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Hub, null, tint = colors.primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text("通知节点", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text("${connection.name} · ${if (nodeEnabled) node.status else "未启用"}", color = colors.onSurfaceVariant, fontSize = 13.sp) }
                            Switch(nodeEnabled, { enabled ->
                                if (enabled && !NotificationNodeSettings.valid(connection)) { editConnection = true }
                                else runCatching {
                                    settings.enabled = enabled
                                    if (enabled) NotificationNodeService.start(context) else context.stopService(Intent(context, NotificationNodeService::class.java))
                                    nodeEnabled = enabled
                                }.onFailure { Toast.makeText(context, "无法启动通知节点，请检查通知权限", Toast.LENGTH_LONG).show() }
                            })
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        if (tablet) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Box(Modifier.weight(2f)) { DashboardField("服务地址", connection.endpoint.ifBlank { "尚未配置" }) }
                            Box(Modifier.weight(1f)) { DashboardField("通道", connection.channel.ifBlank { "未配置" }) }
                        } else DashboardField("服务地址", connection.endpoint.ifBlank { "尚未配置" })
                        DashboardField("告警时间标准", alarmTimeStandardLabel(timeZone))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DashboardField("接收范围", if (nodeEnabled) node.scope else "由 Web 控制台分配") }
                            TextButton(onClick = { editConnection = true }) { Text("连接设置") }; if (nodeEnabled && !node.connected) TextButton(onClick = {
                            context.stopService(Intent(context, NotificationNodeService::class.java))
                            NotificationNodeService.start(context)
                        }) { Text("重新连接") } }
                    }
                    @Composable fun Voice() { DashboardPanel {
                        Text("声音策略", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("接警与本机通知共用报警队列", color = colors.onSurfaceVariant, fontSize = 13.sp)
                        TextButton(onRingtone, Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("默认铃声", Modifier.weight(1f)); Text(ringtone) }
                        TextButton(onLoops, Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("循环次数", Modifier.weight(1f)); Text("$loops 次") }
                        Text("确认后停止；达到次数自动结束", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    } }
                    @Composable fun Local() { DashboardPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("本机通知监听", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Switch(serviceEnabled, { onToggleLocal() }) }
                        Text(when(serviceState) { ServiceState.RUNNING, ServiceState.RUNNING_LIMITED -> "监听中"; ServiceState.DISABLED -> "已停止"; ServiceState.NO_PERMISSION -> "未授权"; ServiceState.INITIALIZING -> "连接中"; ServiceState.HEARTBEAT_TIMEOUT -> "心跳超时"; ServiceState.LISTENER_DISCONNECTED -> "重连中"; else -> "异常" }, color = colors.onSurfaceVariant)
                        Text("$keywordCount 个关键词 · 独立开关", color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = { page = "关键词" }) { Text("管理关键词与应用过滤") }
                        if (serviceState in listOf(ServiceState.LISTENER_DISCONNECTED, ServiceState.HEARTBEAT_TIMEOUT, ServiceState.NO_PERMISSION)) {
                            if (serviceState != ServiceState.NO_PERMISSION) Text("监听未恢复 · 请重试或重新授权", color = colors.error, fontSize = 13.sp)
                            Row { TextButton(onClick = { ListenerRecovery.startFastRecovery(context) }) { Text("立即重试") }; TextButton(onClick = { (context as? Activity)?.let(PermissionUtils::openNotificationListenerSettings) }) { Text("重新授权") } }
                        }
                    } }
                    if (tablet) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { Box(Modifier.weight(1f)) { Voice() }; Box(Modifier.weight(1f)) { Local() } }
                    else { Voice(); Local() }
                    DashboardPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("最近报警", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onHistory) { Text("查看全部") } }
                        if (records.isEmpty()) Text("暂无报警记录", color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                        records.take(4).forEach { record ->
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            Row { Column(Modifier.weight(1f)) { Text(record.keyword); Text(record.sourceApp ?: "本机通知", color = colors.onSurfaceVariant, fontSize = 13.sp) }; Text(formatAlarmTime(record.timestamp, "HH:mm", timeZone), color = colors.onSurfaceVariant, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
    if (editConnection) ConnectionDialog(connection, onDismiss = { editConnection = false }, onSave = { value ->
        if (settings.save(value)) {
            context.stopService(Intent(context, NotificationNodeService::class.java))
            nodeEnabled = false; connection = value; editConnection = false
        } else Toast.makeText(context, "连接配置无效或保存失败", Toast.LENGTH_LONG).show()
    })
}

@Composable private fun DashboardPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}
@Composable private fun DashboardField(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text(value, Modifier.weight(1f), fontSize = 14.sp) }
}
@Composable private fun ConnectionDialog(value: NodeConnection, onDismiss: () -> Unit, onSave: (NodeConnection) -> Unit) {
    var draft by remember { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("连接设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("使用已登记的通知节点身份。保存后请重新启用连接。", fontSize = 13.sp)
            OutlinedTextField(draft.endpoint, { draft = draft.copy(endpoint = it.trim()) }, label = { Text("服务地址 wss://…/ws") }, singleLine = true)
            OutlinedTextField(draft.channel, { draft = draft.copy(channel = it.trim()) }, label = { Text("通道") }, singleLine = true)
            OutlinedTextField(draft.deviceId, { draft = draft.copy(deviceId = it.trim()) }, label = { Text("节点 ID") }, singleLine = true)
            OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("节点名称") }, singleLine = true)
            OutlinedTextField(draft.apiKey, { draft = draft.copy(apiKey = it.trim()) }, label = { Text("节点凭据") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (!NotificationNodeSettings.valid(draft)) Text("请输入有效的 WSS 地址、通道及登记身份", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = { onSave(draft) }, enabled = NotificationNodeSettings.valid(draft)) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
