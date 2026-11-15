package com.example.vigil

import com.example.vigil.node.formatAlarmTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class AlarmTimeTest {
    @Test fun standardOverridesDeviceZoneAcrossMidnightWithoutChangingTheInstant() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"))
        try {
            val instant = Instant.parse("2026-10-01T18:03:04Z").toEpochMilli()
            assertEquals("2026-10-02 02:03:04", formatAlarmTime(instant, "yyyy-MM-dd HH:mm:ss", "Asia/Shanghai"))
            assertEquals("2026-10-01 18:03:04", formatAlarmTime(instant, "yyyy-MM-dd HH:mm:ss", "UTC"))
            assertEquals("2026-10-01 18:03:04", formatAlarmTime(instant, "yyyy-MM-dd HH:mm:ss", null))
        } finally { TimeZone.setDefault(original) }
    }
}
