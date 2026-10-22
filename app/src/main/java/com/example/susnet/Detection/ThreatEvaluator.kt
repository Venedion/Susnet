package com.example.susnet.detection

import com.example.susnet.data.BaselineStore
import com.example.susnet.data.DetectionEventDao
import com.example.susnet.data.DetectionEventEntity

class ThreatEvaluator(
    private val evilTwinDetector: EvilTwinDetector,
    private val tlsPinningMonitor: TlsPinningMonitor,
    private val signalStabilityMonitor: SignalStabilityMonitor,
    private val latencyMonitor: LatencyMonitor,
    private val networkConfigMonitor: NetworkConfigMonitor,
    private val baselineStore: BaselineStore,
    private val detectionEventDao: DetectionEventDao
) {

    data class ThreatResult(
        val threatLevel: Int,
        val bssidChangeScore: Int,
        val tlsChangeScore: Int,
        val signalInstabilityScore: Int,
        val latencyDeviationScore: Int,
        val configChangeScore: Int,
        val currentSsid: String?,
        val bssidDetail: String,
        val tlsDetail: String,
        val signalDetail: String,
        val latencyDetail: String,
        val configDetail: String
    )

    suspend fun evaluateCurrentNetwork(): ThreatResult {
        val evilTwinResult = evilTwinDetector.evaluate()
        val tlsResult = tlsPinningMonitor.consumeTlsResult()
        val signalResult = signalStabilityMonitor.evaluate()
        val latencyResult = latencyMonitor.evaluate()
        val bssidScore = evilTwinResult?.bssidChangeScore ?: 0

        val ssid = evilTwinResult?.currentSsid
        val networkId = ssid?.let { baselineStore.getOrCreateNetworkId(it) }
        val configResult = networkId?.let { networkConfigMonitor.evaluate(it) }
        val configScore = configResult?.configChangeScore ?: 0

        val threatLevel = FuzzyEngine.computeThreatLevel(
            bssidChangeScore = bssidScore,
            tlsChangeScore = tlsResult.score,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore,
            configChangeScore = configScore
        )

        if (networkId != null) {
            detectionEventDao.insertEvent(
                DetectionEventEntity(
                    networkId = networkId,
                    timestamp = System.currentTimeMillis(),
                    threatLevel = threatLevel,
                    bssidChangeScore = bssidScore,
                    tlsChangeScore = tlsResult.score,
                    signalInstabilityScore = signalResult.signalInstabilityScore,
                    latencyDeviationScore = latencyResult.latencyDeviationScore,
                    configChangeScore = configScore
                )
            )
        }

        val notConnected = "Ponsel tidak terhubung ke WiFi, atau alamat WiFi tidak dapat dibaca."

        return ThreatResult(
            threatLevel = threatLevel,
            bssidChangeScore = bssidScore,
            tlsChangeScore = tlsResult.score,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore,
            configChangeScore = configScore,
            currentSsid = ssid,
            bssidDetail = evilTwinResult?.detail ?: notConnected,
            tlsDetail = tlsResult.detail,
            signalDetail = signalResult.detail,
            latencyDetail = latencyResult.detail,
            configDetail = configResult?.detail ?: notConnected
        )
    }
}