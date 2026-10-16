package com.example.susnet.detection

import android.net.wifi.WifiManager
import kotlin.math.sqrt

class SignalStabilityMonitor(
    private val wifiManager: WifiManager,
    private val windowSize: Int = 10
) {

    private val recentRssi = ArrayDeque<Int>()

    data class Result(
        val currentRssi: Int,
        val rssiStdDev: Double,
        val signalInstabilityScore: Int
    )

    fun evaluate(): Result {
        val currentRssi = wifiManager.connectionInfo.rssi

        recentRssi.addLast(currentRssi)
        if (recentRssi.size > windowSize) {
            recentRssi.removeFirst()
        }

        val stdDev = computeStdDev(recentRssi)
        val score = scoreFromStdDev(stdDev)

        return Result(currentRssi, stdDev, score)
    }

    private fun computeStdDev(values: ArrayDeque<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun scoreFromStdDev(stdDev: Double): Int {
        return when {
            stdDev < STABLE_THRESHOLD -> 0
            stdDev < MODERATE_THRESHOLD -> 30
            stdDev < HIGH_THRESHOLD -> 60
            else -> 90
        }
    }

    companion object {
        private const val STABLE_THRESHOLD = 2.0
        private const val MODERATE_THRESHOLD = 6.0
        private const val HIGH_THRESHOLD = 14.0
    }
}
