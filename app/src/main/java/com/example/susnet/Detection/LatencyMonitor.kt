package com.example.susnet.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.sqrt

class LatencyMonitor(
    private val host: String = "8.8.8.8",
    private val port: Int = 443,
    private val timeoutMs: Int = 2000,
    private val windowSize: Int = 10
) {

    private val recentLatencies = ArrayDeque<Long>()

    data class Result(
        val currentLatencyMs: Long,
        val latencyStdDev: Double,
        val latencyDeviationScore: Int,
        val detail: String
    )

    suspend fun evaluate(): Result = withContext(Dispatchers.IO) {
        val latency = measureTcpHandshake()
            ?: return@withContext Result(
                -1,
                0.0,
                0,
                "Latency tidak dapat diukur: koneksi ke $host:$port gagal atau diblokir jaringan ini."
            )

        recentLatencies.addLast(latency)
        if (recentLatencies.size > windowSize) {
            recentLatencies.removeFirst()
        }

        val stdDev = computeStdDev(recentLatencies)

        val detail = if (recentLatencies.size < 2) {
            "Waktu respons ke $host saat ini $latency ms. Baru ${recentLatencies.size} pengukuran, " +
                    "belum cukup untuk menilai kestabilan."
        } else {
            "Waktu respons ke $host saat ini $latency ms. Dari ${recentLatencies.size} pengukuran terakhir, " +
                    "simpangan baku ${"%.0f".format(stdDev)} ms (dianggap stabil jika di bawah " +
                    "${STABLE_THRESHOLD.toInt()} ms)."
        }

        Result(latency, stdDev, scoreFromStdDev(stdDev), detail)
    }

    private fun measureTcpHandshake(): Long? {
        return try {
            Socket().use { socket ->
                val start = System.currentTimeMillis()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                System.currentTimeMillis() - start
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun computeStdDev(values: ArrayDeque<Long>): Double {
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
        private const val STABLE_THRESHOLD = 30.0
        private const val MODERATE_THRESHOLD = 100.0
        private const val HIGH_THRESHOLD = 300.0
    }
}