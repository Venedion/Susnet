package com.example.susnet.detection

import com.example.susnet.data.Confidence
import org.junit.Assert.assertEquals
import org.junit.Test

class EvilTwinDetectorScoreTest {

    @Test
    fun testBrandNewNetworkScore() {
        val score = EvilTwinDetector.bssidScore(true, false, Confidence.LOW)
        assertEquals(0, score)
    }

    @Test
    fun testKnownBssidHighConfidenceScore() {
        val score = EvilTwinDetector.bssidScore(false, true, Confidence.HIGH)
        assertEquals(0, score)
    }

    @Test
    fun testKnownBssidMediumConfidenceScore() {
        val score = EvilTwinDetector.bssidScore(false, true, Confidence.MEDIUM)
        assertEquals(40, score)
    }

    @Test
    fun testKnownBssidLowConfidenceScore() {
        val score = EvilTwinDetector.bssidScore(false, true, Confidence.LOW)
        assertEquals(40, score)
    }

    @Test
    fun testUnknownBssidOnKnownNetworkScore() {
        val score = EvilTwinDetector.bssidScore(false, false, Confidence.LOW)
        assertEquals(60, score)
    }

    @Test
    fun testKnownBssidMediumConfidenceUserTrusted() {
        val score = EvilTwinDetector.bssidScore(false, true, Confidence.MEDIUM, userTrusted = true)
        assertEquals(0, score)
    }

    @Test
    fun testKnownBssidLowConfidenceUserTrusted() {
        val score = EvilTwinDetector.bssidScore(false, true, Confidence.LOW, userTrusted = true)
        assertEquals(0, score)
    }

    @Test
    fun testUnknownBssidUserTrustedReturns60() {
        val score = EvilTwinDetector.bssidScore(false, false, Confidence.LOW, userTrusted = true)
        assertEquals(60, score)
    }
}
