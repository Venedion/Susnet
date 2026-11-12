package com.example.susnet.detection

import android.net.wifi.WifiManager
import kotlin.math.sqrt

class SignalStabilityMonitor(
    private val wifiManager: WifiManager,
    windowSize: Int = 10
) {

    private val rssiWindow = RssiWindow(windowSize)

    data class Result(
        val currentRssi: Int,
        val rssiStdDev: Double,
        val signalInstabilityScore: Int,
        val detail: String,
        val samplesCount: Int
    )

    @Synchronized
    fun sample() {
        val connectionInfo = wifiManager.connectionInfo
        val currentRssi = connectionInfo?.rssi ?: 0
        val currentBssid = connectionInfo?.bssid
        rssiWindow.add(currentBssid, currentRssi)
    }

    @Synchronized
    fun evaluate(): Result {
        sample()
        val connectionInfo = wifiManager.connectionInfo
        val currentRssi = connectionInfo?.rssi ?: 0

        val samplesList = rssiWindow.samples()
        val stdDev = computeStdDev(samplesList)
        val score = computeScore(samplesList)

        val detail = if (samplesList.size < MIN_SAMPLES) {
            "Baru ${samplesList.size} pengukuran, minimal $MIN_SAMPLES diperlukan untuk menilai kestabilan sinyal."
        } else {
            "Kekuatan sinyal saat ini $currentRssi dBm. Dari ${samplesList.size} pengukuran terakhir, " +
                    "simpangan baku ${"%.1f".format(stdDev)} dB (dianggap stabil jika di bawah " +
                    "${STABLE_THRESHOLD.toInt()} dB)."
        }

        return Result(currentRssi, stdDev, score, detail, samplesList.size)
    }

    companion object {
        const val MIN_SAMPLES = 5
        const val STABLE_THRESHOLD = 3.0
        const val MODERATE_THRESHOLD = 8.0
        const val HIGH_THRESHOLD = 15.0

        fun computeStdDev(samples: List<Int>): Double {
            if (samples.size < 2) return 0.0
            val mean = samples.average()
            val variance = samples.sumOf { (it - mean) * (it - mean) } / samples.size
            return sqrt(variance)
        }

        fun scoreFromStdDev(stdDev: Double, samplesCount: Int): Int {
            if (samplesCount < MIN_SAMPLES) return 0
            return when {
                stdDev < STABLE_THRESHOLD -> 0
                stdDev < MODERATE_THRESHOLD -> 30
                stdDev < HIGH_THRESHOLD -> 60
                else -> 90
            }
        }

        fun computeScore(samples: List<Int>): Int {
            if (samples.size < MIN_SAMPLES) return 0
            val stdDev = computeStdDev(samples)
            return scoreFromStdDev(stdDev, samples.size)
        }
    }
}
