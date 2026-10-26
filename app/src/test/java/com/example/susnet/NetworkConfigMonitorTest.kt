package com.example.susnet

import com.example.susnet.data.BaselineStore
import com.example.susnet.data.Confidence
import com.example.susnet.detection.NetworkConfigMonitor
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkConfigMonitorTest {

    private val now = System.currentTimeMillis()
    private val dayMs = 24 * 60 * 60 * 1000L

    @Test
    fun test1_dominantConfig_returns0() {
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = true,
            confidence = Confidence.HIGH,
            gatewayChanged = false,
            dnsChanged = false
        )
        assertEquals(0, score)
    }

    @Test
    fun test2_unseenConfig_gatewayAndDnsDiffer_returns85() {
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = Confidence.LOW,
            gatewayChanged = true,
            dnsChanged = true
        )
        assertEquals(85, score)
    }

    @Test
    fun test3_knownNonDominant_seenCount1_age0_returns85() {
        val confidence = BaselineStore.computeConfidence(1, now, now)
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = confidence,
            gatewayChanged = true,
            dnsChanged = true
        )
        assertEquals(85, score)
    }

    @Test
    fun test4_knownNonDominant_seenCount5_age2Days_returns85() {
        val confidence = BaselineStore.computeConfidence(5, now - 2 * dayMs, now)
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = confidence,
            gatewayChanged = true,
            dnsChanged = true
        )
        assertEquals(85, score)
    }

    @Test
    fun test5_knownNonDominant_seenCount5_age3Days_returns25() {
        val confidence = BaselineStore.computeConfidence(5, now - 3 * dayMs, now)
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = confidence,
            gatewayChanged = true,
            dnsChanged = true
        )
        assertEquals(25, score)
    }

    @Test
    fun test6_onlyGatewayDiffers_lowConfidence_returns55() {
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = Confidence.LOW,
            gatewayChanged = true,
            dnsChanged = false
        )
        assertEquals(55, score)
    }

    @Test
    fun test7_onlyDnsDiffers_lowConfidence_returns65() {
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = Confidence.LOW,
            gatewayChanged = false,
            dnsChanged = true
        )
        assertEquals(65, score)
    }

    @Test
    fun test8_userTrustedConfig_returns0() {
        val score = NetworkConfigMonitor.computeConfigScore(
            hasDominant = true,
            isDominant = false,
            confidence = Confidence.LOW,
            gatewayChanged = true,
            dnsChanged = true,
            userTrusted = true
        )
        assertEquals(0, score)
    }
}
