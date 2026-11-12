package com.example.susnet.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssiWindowTest {

    @Test
    fun testAccumulationSameBssid() {
        val window = RssiWindow(10)
        window.add("AA:BB:CC:DD:EE:FF", -50)
        window.add("AA:BB:CC:DD:EE:FF", -55)
        window.add("AA:BB:CC:DD:EE:FF", -52)

        assertEquals(listOf(-50, -55, -52), window.samples())
    }

    @Test
    fun testBssidChangeClearsAndAddsOne() {
        val window = RssiWindow(10)
        window.add("AA:BB:CC:DD:EE:FF", -50)
        window.add("AA:BB:CC:DD:EE:FF", -55)
        window.add("11:22:33:44:55:66", -60)

        assertEquals(listOf(-60), window.samples())
    }

    @Test
    fun testNullBssidClearsAndDoesNotAdd() {
        val window = RssiWindow(10)
        window.add("AA:BB:CC:DD:EE:FF", -50)
        window.add(null, -55)

        assertTrue(window.samples().isEmpty())
    }

    @Test
    fun testInvalidDefaultBssidClearsAndDoesNotAdd() {
        val window = RssiWindow(10)
        window.add("AA:BB:CC:DD:EE:FF", -50)
        window.add("02:00:00:00:00:00", -55)

        assertTrue(window.samples().isEmpty())

        window.add("", -60)
        assertTrue(window.samples().isEmpty())
    }

    @Test
    fun testWindowLimitTo10() {
        val window = RssiWindow(10)
        for (i in 1..12) {
            window.add("AA:BB:CC:DD:EE:FF", -50 - i)
        }

        val samples = window.samples()
        assertEquals(10, samples.size)
        assertEquals(-53, samples.first()) // 3rd sample added (-50 - 3)
        assertEquals(-62, samples.last())  // 12th sample added (-50 - 12)
    }
}
