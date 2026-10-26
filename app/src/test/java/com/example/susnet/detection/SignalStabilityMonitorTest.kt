package com.example.susnet.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalStabilityMonitorTest {

    @Test
    fun testLessThan5SamplesReturns0() {
        val score = SignalStabilityMonitor.computeScore(listOf(-50, -80, -40, -90))
        assertEquals(0, score)
    }

    @Test
    fun testStable5SamplesReturns0() {
        val score = SignalStabilityMonitor.computeScore(listOf(-50, -51, -50, -51, -50))
        assertEquals(0, score)
    }

    @Test
    fun testStdDevBetween3And8Returns30() {
        val score = SignalStabilityMonitor.computeScore(listOf(-50, -55, -60, -50, -55))
        assertEquals(30, score)
    }

    @Test
    fun testStdDevBetween8And15Returns60() {
        val score = SignalStabilityMonitor.computeScore(listOf(-40, -60, -40, -60, -50))
        assertEquals(60, score)
    }

    @Test
    fun testStdDevAbove15Returns90() {
        val score = SignalStabilityMonitor.computeScore(listOf(-30, -70, -30, -70, -30))
        assertEquals(90, score)
    }

    @Test
    fun testExactStdDev3Returns30() {
        val score = SignalStabilityMonitor.scoreFromStdDev(3.0, 5)
        assertEquals(30, score)
    }

    @Test
    fun testExactStdDev8Returns60() {
        val score = SignalStabilityMonitor.scoreFromStdDev(8.0, 5)
        assertEquals(60, score)
    }
}
