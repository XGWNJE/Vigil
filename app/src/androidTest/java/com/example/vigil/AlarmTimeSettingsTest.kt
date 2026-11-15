package com.example.vigil

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vigil.node.NotificationNodeSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmTimeSettingsTest {
    @Test fun standardSurvivesRecreationAndInvalidSettingPreservesTheCurrentChoice() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("time-test-$name", mode)
        }
        context.getSharedPreferences("relay_credentials", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = NotificationNodeSettings(context)
        assertNull(settings.timeZone)
        assertTrue(settings.saveTimeZone("Asia/Shanghai"))
        assertEquals("Asia/Shanghai", NotificationNodeSettings(context).timeZone)
        assertFalse(settings.saveTimeZone("bad-zone"))
        assertEquals("Asia/Shanghai", settings.timeZone)
        assertTrue(settings.saveTimeZone("UTC"))
        assertEquals("UTC", NotificationNodeSettings(context).timeZone)
    }
}
