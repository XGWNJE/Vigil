package com.example.vigil

import com.example.vigil.data.AlertEndType
import com.example.vigil.data.AlertEnqueueStatus
import com.example.vigil.data.DelayMode
import com.example.vigil.data.DelayPolicy
import com.example.vigil.data.SharedPreferencesHelper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmPersistenceTest {
    // Instrumentation runs as the target UID; use a separate preferences file.
    private val context = object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
            super.getSharedPreferences("instrumentation-$name", mode)
    }
    private lateinit var helper: SharedPreferencesHelper

    @Before fun reset() {
        helper = SharedPreferencesHelper(context)
        context.getSharedPreferences("vigil_prefs", 0).edit().clear().commit()
    }

    @Test fun explicitImmediateSurvivesRecreationOverGlobalDelay() {
        helper.saveDefaultDelayPolicy(DelayPolicy(DelayMode.FIXED, 60_000))
        helper.saveKeywordDelayPolicy("test", DelayPolicy.IMMEDIATE)
        assertEquals(DelayMode.IMMEDIATE, SharedPreferencesHelper(context).resolveDelayPolicy("test").mode)
        helper.saveKeywordDelayPolicy("test", null)
        assertEquals(DelayMode.FIXED, SharedPreferencesHelper(context).resolveDelayPolicy("test").mode)
    }

    @Test fun dueTransferRetainsIdentityAndOnlyHappensOnce() {
        val item = helper.addScheduledAlert("test", "pkg", "App", null, 2, 1000, 500, "message")!!
        assertEquals(AlertEnqueueStatus.STARTED, helper.transferScheduledAlert(item.id, 1000).status)
        val reloaded = SharedPreferencesHelper(context)
        assertTrue(reloaded.getScheduledAlerts().isEmpty())
        assertEquals(item.id, reloaded.getActiveAlert()!!.id)
        assertEquals("message", reloaded.getActiveAlert()!!.snippet)
        reloaded.transferScheduledAlert(item.id, 1001)
        assertEquals(1, reloaded.getAlertQueue().size)
        // Transfer must not reset the cooldown to the delayed trigger time.
        assertNotNull(reloaded.addScheduledAlert("test", "pkg", "App", null, 2, 200_000, 60_501))
    }

    @Test fun rejectedDelayedRequestDoesNotOccupyCooldown() {
        repeat(20) { i -> helper.addScheduledAlert("full$i", "pkg", null, null, 1, 10_000, 1000) }
        assertNull(helper.addScheduledAlert("retry", "pkg", null, null, 1, 10_000, 1000))
        helper.removeScheduledAlert(helper.getScheduledAlerts().first().id)
        assertNotNull(helper.addScheduledAlert("retry", "pkg", null, null, 1, 10_000, 1001))
    }

    @Test fun confirmationPersistsWithoutAnyServiceEventSubscriber() {
        val first = helper.enqueueOrAggregateAlert("same", "pkg", "App", null, 1, 1000, 0, "first").active!!
        val second = helper.enqueueOrAggregateAlert("same", "pkg", "App", null, 1, 1000, 0, "second").affected!!
        assertTrue(SharedPreferencesHelper(context).finishActiveAlert(first.id, AlertEndType.MANUAL).success)
        val reloaded = SharedPreferencesHelper(context)
        assertEquals(second.id, reloaded.getActiveAlert()!!.id)
        assertFalse(reloaded.finishActiveAlert(first.id, AlertEndType.MANUAL).success)
        assertTrue(reloaded.finishActiveAlert(second.id, AlertEndType.ERROR).success)
        val records = reloaded.getAlertHistory()
        assertEquals(2, records.size)
        assertEquals(2, records.map { it.id }.distinct().size)
        assertNull(SharedPreferencesHelper(context).getActiveAlert())
    }

    @Test fun fullQueueKeepsDelayedItemForRetry() {
        repeat(20) { i -> helper.enqueueOrAggregateAlert("full$i", "pkg", null, null, 1, 1000, 0) }
        val scheduled = helper.addScheduledAlert("later", "pkg", null, null, 1, 2000, 1000)!!
        assertEquals(AlertEnqueueStatus.QUEUE_FULL, helper.transferScheduledAlert(scheduled.id, 2000).status)
        assertEquals(scheduled.id, helper.getScheduledAlerts().single().id)
        helper.finishActiveAlert(helper.getActiveAlert()!!.id, AlertEndType.MANUAL)
        assertEquals(AlertEnqueueStatus.QUEUED, helper.transferScheduledAlert(scheduled.id, 2001).status)
        assertTrue(helper.getScheduledAlerts().isEmpty())
    }

    @Test fun emptyDailyTimesCannotBeSaved() {
        try {
            helper.saveDefaultDelayPolicy(DelayPolicy(DelayMode.SCHEDULED))
            fail("Empty daily schedule accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(DelayMode.IMMEDIATE, helper.getDefaultDelayPolicy().mode)
    }

    @Test fun failedDiskCommitDoesNotConsumeCooldownOrAcceptAlert() {
        val realPrefs = context.getSharedPreferences("failure", 0)
        realPrefs.edit().clear().commit()
        var rejectNextCommit = true
        val failingPrefs = object : android.content.SharedPreferences by realPrefs {
            override fun edit(): android.content.SharedPreferences.Editor {
                val realEditor = realPrefs.edit()
                return object : android.content.SharedPreferences.Editor by realEditor {
                    override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor {
                        realEditor.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        // Emulate Android's memory update before a failed disk write.
                        val succeeds = !rejectNextCommit
                        rejectNextCommit = false
                        realEditor.commit()
                        return succeeds
                    }
                }
            }
        }
        val failingContext = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = failingPrefs
        }
        val failing = SharedPreferencesHelper(failingContext)
        assertNull(failing.addScheduledAlert("retry", "pkg", null, null, 1, 2000, 1000))
        assertTrue(failing.getScheduledAlerts().isEmpty())
        assertNotNull(failing.addScheduledAlert("retry", "pkg", null, null, 1, 2000, 1001))
    }
}
