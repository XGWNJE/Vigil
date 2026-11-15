package com.example.vigil.node

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun validAlarmTimeZone(value: String) = value == "Asia/Shanghai" || value == "UTC"

fun alarmTimeStandardLabel(value: String?) = when (value) {
    "Asia/Shanghai" -> "北京时间（UTC+8）"
    "UTC" -> "UTC（UTC+0）"
    else -> "跟随设备时区"
}

fun formatAlarmTime(millis: Long, pattern: String, timeZone: String?): String =
    SimpleDateFormat(pattern, Locale.CHINA).apply {
        this.timeZone = if (timeZone != null && validAlarmTimeZone(timeZone)) TimeZone.getTimeZone(timeZone) else TimeZone.getDefault()
    }.format(Date(millis))
