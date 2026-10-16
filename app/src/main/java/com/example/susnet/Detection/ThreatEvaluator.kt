package com.example.susnet.detection

import com.example.susnet.data.BaselineStore
import com.example.susnet.data.DetectionEventDao
import com.example.susnet.data.DetectionEventEntity

class ThreatEvaluator(
    private val evilTwinDetector: EvilTwinDetector,
    private val tlsPinningMonitor: TlsPinningMonitor,
    private val signalStabilityMonitor: SignalStabilityMonitor,
    private val latencyMonitor: LatencyMonitor,
    private val baselineStore: BaselineStore,
    private val detectionEventDao: DetectionEventDao
) {

    data class ThreatResult(
        val threatLevel: Int,
        val bssidChangeScore: Int,
        val tlsChangeScore: Int,
        val signalInstabilityScore: Int,
        val latencyDeviationScore: Int,
        val currentSsid: String?
    )

    suspend fun evaluateCurrentNetwork(): ThreatResult {
        val evilTwinResult = evilTwinDetector.evaluate()
        val tlsScore = tlsPinningMonitor.consumeTlsChangeScore()
        val signalResult = signalStabilityMonitor.evaluate()
        val latencyResult = latencyMonitor.evaluate()
        val bssidScore = evilTwinResult?.bssidChangeScore ?: 0

        val threatLevel = FuzzyEngine.computeThreatLevel(
            bssidChangeScore = bssidScore,
            tlsChangeScore = tlsScore,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore
        )

        evilTwinResult?.currentSsid?.let { ssid ->
            val networkId = baselineStore.getOrCreateNetworkId(ssid)
            detectionEventDao.insertEvent(
                DetectionEventEntity(
                    networkId = networkId,
                    timestamp = System.currentTimeMillis(),
                    threatLevel = threatLevel,
                    bssidChangeScore = bssidScore,
                    tlsChangeScore = tlsScore,
                    signalInstabilityScore = signalResult.signalInstabilityScore,
                    latencyDeviationScore = latencyResult.latencyDeviationScore
                )
            )
        }

        return ThreatResult(
            threatLevel = threatLevel,
            bssidChangeScore = bssidScore,
            tlsChangeScore = tlsScore,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore,
            currentSsid = evilTwinResult?.currentSsid
        )
    }
}