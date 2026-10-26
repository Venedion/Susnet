package com.example.susnet

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalLatencySummaryTest {

    @Test
    fun testSignalSummaryScore0() {
        val summary = MainActivity.signalSummary(-50, 0)
        assertEquals("-50 dBm · stabil", summary)
    }

    @Test
    fun testSignalSummaryScore30() {
        val summary = MainActivity.signalSummary(-50, 30)
        assertEquals("-50 dBm · sedikit berubah", summary)
    }

    @Test
    fun testSignalSummaryScore35() {
        val summary = MainActivity.signalSummary(-50, 35)
        assertEquals("-50 dBm · naik-turun", summary)
    }

    @Test
    fun testLatencySummaryScore0() {
        val summary = MainActivity.latencySummary(20L, 0)
        assertEquals("20 ms · stabil", summary)
    }

    @Test
    fun testLatencySummaryScore30() {
        val summary = MainActivity.latencySummary(20L, 30)
        assertEquals("20 ms · sedikit berubah", summary)
    }

    @Test
    fun testLatencySummaryScore35() {
        val summary = MainActivity.latencySummary(20L, 35)
        assertEquals("20 ms · naik-turun", summary)
    }

    @Test
    fun testLatencySummaryNegativeLatency() {
        val summary = MainActivity.latencySummary(-1L, 0)
        assertEquals("Tidak dapat diukur", summary)
    }
}
