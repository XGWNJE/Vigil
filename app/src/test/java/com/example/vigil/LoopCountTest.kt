package com.example.vigil

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopCountTest {
    @Test
    fun normalizeLoopCount_clampsLegacyAndOutOfRangeValues() {
        assertEquals(1, SharedPreferencesHelper.normalizeLoopCount(-1))
        assertEquals(10, SharedPreferencesHelper.normalizeLoopCount(0))
        assertEquals(1, SharedPreferencesHelper.normalizeLoopCount(1))
        assertEquals(5, SharedPreferencesHelper.normalizeLoopCount(5))
        assertEquals(10, SharedPreferencesHelper.normalizeLoopCount(10))
        assertEquals(10, SharedPreferencesHelper.normalizeLoopCount(50))
        assertEquals(10, SharedPreferencesHelper.normalizeLoopCount(100))
    }
}
