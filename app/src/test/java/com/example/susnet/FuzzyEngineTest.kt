package com.example.susnet

import com.example.susnet.detection.FuzzyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyEngineTest {

    @Test
    fun testExpectedValues() {
        assertEquals(18, FuzzyEngine.computeThreatLevel(0, 0, 0, 0, 0))
        assertEquals(18, FuzzyEngine.computeThreatLevel(20, 0, 0, 0, 0))
        assertEquals(40, FuzzyEngine.computeThreatLevel(40, 0, 0, 0, 0))
        assertEquals(59, FuzzyEngine.computeThreatLevel(60, 0, 0, 0, 0))
        assertEquals(81, FuzzyEngine.computeThreatLevel(0, 90, 0, 0, 0))
        assertEquals(30, FuzzyEngine.computeThreatLevel(0, 0, 30, 0, 0))
        assertEquals(50, FuzzyEngine.computeThreatLevel(0, 0, 0, 60, 0))
        assertEquals(25, FuzzyEngine.computeThreatLevel(0, 0, 0, 0, 25))
        assertEquals(54, FuzzyEngine.computeThreatLevel(0, 0, 0, 0, 55))
        assertEquals(81, FuzzyEngine.computeThreatLevel(0, 0, 0, 0, 85))
        assertEquals(68, FuzzyEngine.computeThreatLevel(60, 0, 0, 0, 85))
    }

    @Test
    fun testAllDiscreteValuesBelow35ReturnBelow35() {
        val bssidValues = listOf(0, 20)
        val tlsValues = listOf(0)
        val signalValues = listOf(0, 30)
        val latencyValues = listOf(0, 30)
        val configValues = listOf(0, 25)

        for (bssid in bssidValues) {
            for (tls in tlsValues) {
                for (signal in signalValues) {
                    for (latency in latencyValues) {
                        for (config in configValues) {
                            val threat = FuzzyEngine.computeThreatLevel(bssid, tls, signal, latency, config)
                            assertTrue(
                                "Expected threat < 35 for inputs ($bssid, $tls, $signal, $latency, $config) but got $threat",
                                threat < 35
                            )
                        }
                    }
                }
            }
        }
    }

    // Analisis sensitivitas kombinasi BSSID dan Konfigurasi (bukan target tetapi perilaku terverifikasi)
    @Test
    fun testSensitivityBssid40AndConfig() {
        assertEquals(64, FuzzyEngine.computeThreatLevel(40, 0, 0, 0, 55))
        assertEquals(62, FuzzyEngine.computeThreatLevel(40, 0, 0, 0, 65))
        assertEquals(68, FuzzyEngine.computeThreatLevel(40, 0, 0, 0, 85))
    }

    @Test
    fun testSensitivityBssid60AndConfig() {
        assertEquals(64, FuzzyEngine.computeThreatLevel(60, 0, 0, 0, 55))
        assertEquals(62, FuzzyEngine.computeThreatLevel(60, 0, 0, 0, 65))
        assertEquals(68, FuzzyEngine.computeThreatLevel(60, 0, 0, 0, 85))
    }

    @Test
    fun testSensitivityBssid20AndConfig() {
        assertEquals(63, FuzzyEngine.computeThreatLevel(20, 0, 0, 0, 65))
        assertEquals(81, FuzzyEngine.computeThreatLevel(20, 0, 0, 0, 85))
    }

    @Test
    fun testSensitivityBssid0AndConfig() {
        assertEquals(81, FuzzyEngine.computeThreatLevel(0, 0, 0, 0, 85))
    }
}
