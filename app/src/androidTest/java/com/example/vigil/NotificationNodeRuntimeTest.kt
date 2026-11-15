package com.example.vigil

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vigil.node.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/** 配合 VisionGuard/server/test/notification-node-fixture.cjs 的隔离服务运行。 */
@RunWith(AndroidJUnit4::class)
class NotificationNodeRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val base = InstrumentationRegistry.getArguments().getString("relayFixture", "")
    private fun request(route: String): JSONObject {
        val connection = URL("$base$route").openConnection()
        connection.connectTimeout = 3_000; connection.readTimeout = 3_000
        return JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
    }
    private fun await(timeout: Long = 10_000, condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + timeout
        while (!condition() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertTrue("Runtime condition timed out", condition())
    }
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    @Test fun backgroundReceiptScopePlaybackAndServiceOutage() {
        assumeTrue("需要隔离服务参数 relayFixture", base == "http://10.0.2.2:4319")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        context.stopService(Intent(context, MyNotificationListenerService::class.java))
        context.stopService(Intent(context, NotificationNodeService::class.java))
        context.getSharedPreferences("vigil_prefs",0).edit().clear().putBoolean("is_first_launch",false)
            .putBoolean("has_shown_donate_dialog",true).putString("debug_update_api_base","http://127.0.0.1:1").commit()
        val config = request("/config")
        val settings = NotificationNodeSettings(context)
        val prefs = SharedPreferencesHelper(context)
        assertTrue(settings.save(NodeConnection(config.getString("endpoint"),config.getString("channel"),config.getString("deviceId"),config.getString("name"),config.getString("apiKey"))))
        prefs.saveRingtoneValue(RingtoneLibrary.SILENT_VALUE)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { settings.enabled = true; NotificationNodeService.start(it) }
            await { NotificationNodeService.state.value.connected }
            assertFalse(prefs.getServiceEnabledState())
            request("/scope?mode=selected")
            await { NotificationNodeService.state.value.scope == "指定 1 个节点或来源" }
            val front = request("/emit?kind=front").getString("alertId")
            await { prefs.getActiveAlert()?.id == front }
            await { request("/state").getJSONArray("receipts").toString().contains(front) }
            request("/emit?kind=side"); request("/emit?kind=sensor")
            Thread.sleep(3_500)
            assertEquals(listOf(front),prefs.getAlertQueue().map { it.id })
            assertTrue(prefs.finishActiveAlert(front,AlertEndType.MANUAL).success)
            request("/scope?mode=all")
            await { NotificationNodeService.state.value.scope == "全部检测节点" }
            prefs.saveRingtoneValue(RingtoneLibrary.presetValue(context,RingtoneLibrary.PRESETS.last()))
            prefs.saveDefaultLoopCount(10)
            shell("input keyevent HOME")
            val sensor = request("/emit?kind=sensor").getString("alertId")
            await { prefs.getActiveAlert()?.id == sensor }
            await { shell("dumpsys media.player").contains("packageName: com.example.vigil") }
            await { request("/state").getJSONArray("receipts").toString().contains(sensor) }
            assertTrue(prefs.finishActiveAlert(sensor,AlertEndType.MANUAL).success)
            await { !shell("dumpsys media.player").contains("packageName: com.example.vigil") }
            prefs.saveRingtoneValue(RingtoneLibrary.SILENT_VALUE)
            request("/scope?mode=none")
            await { NotificationNodeService.state.value.scope == "指定 0 个节点或来源" }
            request("/pause-server")
            await(55_000) { prefs.getAlertQueue().any { it.keyword == "服务中断" } }
            val outage = prefs.getAlertQueue().single()
            Thread.sleep(6_500)
            assertEquals(listOf(outage.id),prefs.getAlertQueue().map { it.id })
            request("/resume-server")
            await(40_000) { NotificationNodeService.state.value.connected }
            assertTrue(prefs.finishActiveAlert(outage.id,AlertEndType.MANUAL).success)
            Thread.sleep(3_500)
            assertTrue(prefs.getAlertQueue().isEmpty())
        } finally {
            runCatching { request("/resume-server") }
            settings.enabled = false
            context.stopService(Intent(context,NotificationNodeService::class.java))
            context.stopService(Intent(context,MyNotificationListenerService::class.java))
            scenario.onActivity { it.intent = context.packageManager.getLaunchIntentForPackage(context.packageName) }
            scenario.close()
            context.getSharedPreferences("vigil_prefs",0).edit().clear().commit()
            context.getSharedPreferences("relay_credentials",0).edit().clear().commit()
        }
    }
}
