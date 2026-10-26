package com.example.susnet.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvilTwinDetectorReadableTest {

    @Test
    fun testSsidNull() {
        assertFalse(EvilTwinDetector.isReadable(null, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testSsidEmpty() {
        assertFalse(EvilTwinDetector.isReadable("", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testSsidUnknownLowercase() {
        assertFalse(EvilTwinDetector.isReadable("<unknown ssid>", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testSsidUnknownUppercase() {
        assertFalse(EvilTwinDetector.isReadable("<UNKNOWN SSID>", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun testBssidRestrictedMac() {
        assertFalse(EvilTwinDetector.isReadable("MyWiFi", "02:00:00:00:00:00"))
    }

    @Test
    fun testValidSsidAndBssid() {
        assertTrue(EvilTwinDetector.isReadable("MyWiFi", "AA:BB:CC:DD:EE:FF"))
    }
}
