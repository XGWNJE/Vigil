package com.example.vigil

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vigil.ui.monitoring.MonitoringViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(32)
            runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun absentListenerSignalLeavesInitializationAndOffersRecovery() {
        compose.mainClock.autoAdvance = false
        val component = android.content.ComponentName(context, MyNotificationListenerService::class.java)
        val originalState = context.packageManager.getComponentEnabledSetting(component)
        context.stopService(android.content.Intent(context, MyNotificationListenerService::class.java))
        context.packageManager.setComponentEnabledSetting(component,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP)
        val permission = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cmd notification allow_listener ${context.packageName}/${MyNotificationListenerService::class.java.name}")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(permission).use { it.readBytes() }
        Thread.sleep(300)
        VigilEventBus.heartbeat.resetReplayCache()
        context.getSharedPreferences("vigil_prefs", 0).edit().clear()
            .putBoolean("service_enabled", true).putBoolean("listener_connected", false)
            .putBoolean("is_first_launch", false).putBoolean("has_shown_donate_dialog", true)
            .putString("debug_update_api_base", "http://127.0.0.1:1").commit()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitText("连接中")
            awaitText("心跳超时")
            compose.onNodeWithText("立即重试").assertIsDisplayed()
            compose.onNodeWithText("重新授权").assertIsDisplayed()
        } finally {
            scenario?.close()
            context.getSharedPreferences("vigil_prefs", 0).edit().clear().commit()
            context.packageManager.setComponentEnabledSetting(component, originalState,
                android.content.pm.PackageManager.DONT_KILL_APP)
        }
    }

    @Test fun ringtoneLastEntryReachableAndEmptyDailyScheduleCannotSave() {
        compose.mainClock.autoAdvance = false
        val grant = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(grant).use { it.readBytes() }
        context.getSharedPreferences("vigil_prefs", 0).edit().clear()
            .putBoolean("is_first_launch", false).putBoolean("has_shown_donate_dialog", true)
            .putString("debug_update_api_base", "http://127.0.0.1:1").commit()
        val prefs = SharedPreferencesHelper(context)
        val files = (1..30).map { index ->
            java.io.File(RingtoneLibrary.libraryDir(context), "ui-fixture-$index.wav").also {
                it.writeBytes(byteArrayOf(0))
                prefs.putRingtoneLibraryEntry(it.name, "Fixture %02d".format(index))
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitText("已停止")
            compose.onNodeWithContentDescription("设置").performClick()
            compose.mainClock.advanceTimeBy(500)
            awaitText("默认铃声")
            compose.onNodeWithText("默认铃声").performClick()
            awaitText("选择铃声")
            compose.onNodeWithText("Fixture 30").performScrollTo()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Fixture 30").assertIsDisplayed().performClick()
            assertTrue(prefs.getRingtoneValue().orEmpty().endsWith("ui-fixture-30.wav"))
            compose.onNodeWithText("延时报警").performScrollTo()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("延时报警").performClick()
            awaitText("每日定点")
            compose.onNodeWithText("每日定点").performScrollTo()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("每日定点").performClick()
            compose.mainClock.advanceTimeBy(100)
            awaitText("还没有时间点，先添加一个")
            compose.onNodeWithText("确定").assertIsNotEnabled()
            assertEquals(DelayMode.IMMEDIATE, prefs.getDefaultDelayPolicy().mode)
            compose.onNodeWithText("取消").performClick()
            compose.mainClock.advanceTimeBy(100)
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("报警记录").performClick()
            awaitText("暂无报警记录")
            scenario.onActivity {
                repeat(2) {
                    val item = prefs.enqueueOrAggregateAlert("Same timestamp", "test", "UI fixture",
                        RingtoneLibrary.SILENT_VALUE, 1, now = System.currentTimeMillis(), cooldownMs = 0).active!!
                    assertTrue(prefs.finishActiveAlert(item.id, AlertEndType.MANUAL).success)
                }
            }
            awaitText("2 RECORDS")
            assertEquals(2, prefs.getAlertHistory().map { it.id }.distinct().size)
        } finally {
            scenario.close()
            files.forEach { it.delete() }
            context.getSharedPreferences("vigil_prefs", 0).edit().clear().commit()
        }
    }

    @Test fun alertConfirmationRemainsReachableDuringUpdateAndDownload() {
        // The production ripple uses a continuous frame loop; step it explicitly.
        compose.mainClock.autoAdvance = false
        val grant = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(grant).use { it.readBytes() }
        val server = ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        val releaseDownload = CountDownLatch(1)
        val base = "http://127.0.0.1:${server.localPort}"
        val thread = Thread {
            try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = """{"tag_name":"v99.0.0","body":"Local UI fixture","html_url":"$base","assets":[{"name":"app.apk","browser_download_url":"$base/app.apk","size":4,"digest":"sha256:invalid"}]}""".toByteArray()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n".toByteArray())
                    client.getOutputStream().flush()
                    releaseDownload.await(30, TimeUnit.SECONDS)
                    client.getOutputStream().write("fail".toByteArray())
                }
            } catch (_: java.io.IOException) { }
        }
        thread.start()
        val prefs = SharedPreferencesHelper(context)
        context.getSharedPreferences("vigil_prefs", 0).edit().clear()
            .putBoolean("is_first_launch", false).putBoolean("has_shown_donate_dialog", true)
            .putString("debug_update_api_base", base).commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitText("发现新版本")
            fun trigger(keyword: String) {
                prefs.enqueueOrAggregateAlert(keyword, "test", "UI fixture", RingtoneLibrary.SILENT_VALUE,
                    1, cooldownMs = 0, snippet = "Saved message for $keyword")
                scenario.onActivity { ViewModelProvider(it)[MonitoringViewModel::class.java].syncAlertDialogWithPersistentState() }
                awaitText("已知晓，停止报警")
                Thread.sleep(300)
                compose.mainClock.advanceTimeBy(400)
                compose.onNodeWithText("发现新版本").assertDoesNotExist()
                compose.onNodeWithText("正在下载更新").assertDoesNotExist()
                compose.onNodeWithText("已知晓，停止报警").assertIsDisplayed().performClick()
                assertNull(SharedPreferencesHelper(context).getActiveAlert())
            }
            trigger("Update overlap")
            awaitText("发现新版本")
            compose.onNodeWithText("更新").performClick()
            awaitText("正在下载更新")
            trigger("Download overlap")
            awaitText("正在下载更新")
            releaseDownload.countDown()
            awaitText("检查更新失败")
            compose.onNodeWithText("下载更新失败，请检查网络后重试").assertIsDisplayed()

            compose.onNodeWithText("知道了").performClick()
            prefs.enqueueOrAggregateAlert("First queued", "test", "First source", RingtoneLibrary.SILENT_VALUE,
                1, cooldownMs = 0, snippet = "First summary")
            prefs.enqueueOrAggregateAlert("Second queued", "test", "Second source", RingtoneLibrary.SILENT_VALUE,
                1, cooldownMs = 0, snippet = "Second summary")
            scenario.onActivity { ViewModelProvider(it)[MonitoringViewModel::class.java].syncAlertDialogWithPersistentState() }
            awaitText("First summary")
            Thread.sleep(300)
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("已知晓，停止报警").performClick()
            awaitText("Second summary")
            compose.onNodeWithText("Second source").assertExists()
            Thread.sleep(300)
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("已知晓，停止报警").performClick()
            assertNull(prefs.getActiveAlert())
        } finally {
            releaseDownload.countDown()
            server.close()
            thread.join(5000)
            // The service replaces the Activity Intent with its alert intent. Restore
            // the launch identity so ActivityScenario observes the destroy event.
            scenario.onActivity { activity ->
                activity.intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    .setClass(context, MainActivity::class.java)
            }
            scenario.close()
            context.getSharedPreferences("vigil_prefs", 0).edit().remove("debug_update_api_base").commit()
        }
    }
}
