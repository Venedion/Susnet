package com.example.susnet.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupTest {

    private val now = 1000000L

    @Test
    fun testFirstNotificationReturnsTrue() {
        val map = emptyMap<String, Long>()
        assertTrue(NotificationHelper.shouldNotify(map, "WiFi_1:WASPADA", now))
    }

    @Test
    fun testNotificationWithin10MinutesReturnsFalse() {
        val map = mapOf("WiFi_1:WASPADA" to now)
        val time5MinLater = now + (5 * 60 * 1000L)
        assertFalse(NotificationHelper.shouldNotify(map, "WiFi_1:WASPADA", time5MinLater))
    }

    @Test
    fun testNotificationAfter10MinutesReturnsTrue() {
        val map = mapOf("WiFi_1:WASPADA" to now)
        val time11MinLater = now + (11 * 60 * 1000L)
        assertTrue(NotificationHelper.shouldNotify(map, "WiFi_1:WASPADA", time11MinLater))
    }

    @Test
    fun testDifferentKeysReturnsTrue() {
        val map = mapOf("WiFi_1:WASPADA" to now)
        assertTrue(NotificationHelper.shouldNotify(map, "WiFi_2:WASPADA", now))
    }
}
