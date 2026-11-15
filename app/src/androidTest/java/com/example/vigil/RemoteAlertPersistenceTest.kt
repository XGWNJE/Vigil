package com.example.vigil

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteAlertPersistenceTest {
    private val context = object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("remote-test-$name", mode)
    }
    private lateinit var prefs: SharedPreferencesHelper
    @Before fun setup() {
        context.getSharedPreferences("vigil_prefs", 0).edit().clear().commit()
        prefs = SharedPreferencesHelper(context)
        prefs.saveRingtoneValue(RingtoneLibrary.SILENT_VALUE)
    }
    @Test fun repeatReceiptAfterConfirmationAndRecreationDoesNotReplay() {
        assertTrue(prefs.acceptRemoteAlert("server-id", "视觉检测", "门口 · 前门", "person", 1_000, 31_000, 2_000))
        assertEquals("server-id", prefs.getActiveAlert()!!.id)
        assertEquals("门口 · 前门", prefs.getActiveAlert()!!.sourceApp)
        assertEquals(RingtoneLibrary.SILENT_VALUE, prefs.getActiveAlert()!!.ringtoneUri)
        assertTrue(prefs.finishActiveAlert("server-id", AlertEndType.MANUAL).success)
        val reopened = SharedPreferencesHelper(context)
        assertTrue(reopened.acceptRemoteAlert("server-id", "视觉检测", "门口 · 前门", "person", 1_000, 31_000, 3_000))
        assertTrue(reopened.getAlertQueue().isEmpty())
        assertEquals(1, reopened.getAlertHistory().size)
    }
    @Test fun fullQueueDoesNotConfirmAndCanRetryWhileStillFresh() {
        repeat(20) { assertTrue(prefs.acceptRemoteAlert("full-$it", "检测", "节点", "text", 1_000, 31_000, 2_000)) }
        assertFalse(prefs.acceptRemoteAlert("retry", "检测", "节点", "text", 1_000, 31_000, 2_000))
        assertTrue(prefs.finishActiveAlert("full-0", AlertEndType.MANUAL).success)
        assertTrue(prefs.acceptRemoteAlert("retry", "检测", "节点", "text", 1_000, 31_000, 3_000))
        assertEquals("retry", prefs.getAlertQueue().last().id)
    }
    @Test fun expiredAndUnboundedMessagesNeverEnterQueue() {
        assertFalse(prefs.acceptRemoteAlert("old", "检测", "节点", "text", 1_000, 2_000, 2_000))
        assertFalse(prefs.acceptRemoteAlert("future", "检测", "节点", "text", 40_000, 60_000, 2_000))
        assertFalse(prefs.acceptRemoteAlert("long", "检测", "节点", "text", 1_000, 31_001, 2_000))
        assertTrue(prefs.getAlertQueue().isEmpty())
    }
}
