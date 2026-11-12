package com.example.susnet.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCheckPolicyTest {

    @Test
    fun testUnreadableCurrentBssid_ReturnsFalse() {
        assertFalse(AutoCheckPolicy.shouldRun("AA:BB:CC:DD:EE:FF", null, 0L, 1000L))
        assertFalse(AutoCheckPolicy.shouldRun("AA:BB:CC:DD:EE:FF", "", 0L, 1000L))
        assertFalse(AutoCheckPolicy.shouldRun("AA:BB:CC:DD:EE:FF", "02:00:00:00:00:00", 0L, 1000L))
    }

    @Test
    fun testNullLastBssid_ReturnsTrue() {
        assertTrue(AutoCheckPolicy.shouldRun(null, "AA:BB:CC:DD:EE:FF", 0L, 1000L))
    }

    @Test
    fun testDifferentBssid_ReturnsTrue() {
        assertTrue(AutoCheckPolicy.shouldRun("11:22:33:44:55:66", "AA:BB:CC:DD:EE:FF", 1000L, 2000L))
    }

    @Test
    fun testSameBssidFourMinGap_ReturnsFalse() {
        val lastCheckAt = 1000000L
        val fourMinLater = lastCheckAt + (4 * 60 * 1000L)
        assertFalse(AutoCheckPolicy.shouldRun("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF", lastCheckAt, fourMinLater))
    }

    @Test
    fun testSameBssidExactFiveMinGap_ReturnsTrue() {
        val lastCheckAt = 1000000L
        val fiveMinLater = lastCheckAt + (5 * 60 * 1000L)
        assertTrue(AutoCheckPolicy.shouldRun("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF", lastCheckAt, fiveMinLater))
    }
}
