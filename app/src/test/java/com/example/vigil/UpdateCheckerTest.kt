// src/test/java/com/example/vigil/UpdateCheckerTest.kt
package com.example.vigil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查纯逻辑的 JVM 单元测试。
 * 只测不依赖 Android 的 [UpdateChecker.isNewer] 与 [UpdateChecker.cleanReleaseNotes]，
 * 网络/下载逻辑在真机/模拟器做端到端验证。
 */
class UpdateCheckerTest {

    @Test
    fun `newer_patch_version_is_true`() {
        assertTrue(UpdateChecker.isNewer("1.17.0", "1.16.0"))
    }

    @Test
    fun `same_version_is_not_newer`() {
        assertFalse(UpdateChecker.isNewer("1.16.0", "1.16.0"))
    }

    @Test
    fun `older_remote_is_not_newer`() {
        assertFalse(UpdateChecker.isNewer("1.15.0", "1.16.0"))
    }

    @Test
    fun `major_version_newer_is_true`() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `v_prefix_is_handled`() {
        // GitHub tag 带 v 前缀
        assertTrue(UpdateChecker.isNewer("v1.17.0", "1.16.0"))
    }

    @Test
    fun `two_component_versions_compare`() {
        assertTrue(UpdateChecker.isNewer("1.17", "1.16.9"))
        assertFalse(UpdateChecker.isNewer("1.16", "1.16.0"))
    }

    @Test
    fun `pre_release_suffix_ignored`() {
        assertTrue(UpdateChecker.isNewer("1.17.0-beta", "1.16.0"))
        assertFalse(UpdateChecker.isNewer("1.16.0-beta", "1.16.0"))
    }

    @Test
    fun `garbage_version_is_not_newer`() {
        assertFalse(UpdateChecker.isNewer("", "1.16.0"))
        assertFalse(UpdateChecker.isNewer("abc", "1.16.0"))
        assertFalse(UpdateChecker.isNewer("1.16.0", "not-a-version"))
    }

    @Test
    fun `clean_release_notes_strips_heading_hashes`() {
        val raw = "## 循环次数更可控\n\n**优化**\n- 循环次数改为滑杆\n\n**修复**\n- 修复某 bug"
        val cleaned = UpdateChecker.cleanReleaseNotes(raw)
        assertEquals("循环次数更可控\n**优化**\n- 循环次数改为滑杆\n**修复**\n- 修复某 bug", cleaned)
    }

    @Test
    fun `clean_release_notes_blank_body_fallback`() {
        assertEquals("（暂无发版说明）", UpdateChecker.cleanReleaseNotes(""))
        assertEquals("（暂无发版说明）", UpdateChecker.cleanReleaseNotes("   \n\n  "))
    }
}
