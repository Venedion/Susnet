package com.example.susnet.detection

import com.example.susnet.StatusLevel
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.DetectionEventDao
import com.example.susnet.data.DetectionEventEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ThreatEvaluator(
    private val evilTwinDetector: EvilTwinDetector,
    private val tlsPinningMonitor: TlsPinningMonitor,
    private val httpIntegrityMonitor: HttpIntegrityMonitor,
    private val signalStabilityMonitor: SignalStabilityMonitor,
    private val latencyMonitor: LatencyMonitor,
    private val networkConfigMonitor: NetworkConfigMonitor,
    private val baselineStore: BaselineStore,
    private val detectionEventDao: DetectionEventDao,
    private val wifiSecurityChecker: WifiSecurityChecker
) {

    data class ThreatResult(
        val isConnected: Boolean = true,
        val threatLevel: Int,
        val bssidChangeScore: Int,
        val tlsChangeScore: Int,
        val signalInstabilityScore: Int,
        val latencyDeviationScore: Int,
        val configChangeScore: Int,
        val currentSsid: String?,
        val currentBssid: String? = null,
        val bssidDetail: String,
        val tlsDetail: String,
        val signalDetail: String,
        val latencyDetail: String,
        val configDetail: String,
        val rssi: Int,
        val latencyMs: Long,
        val gatewayIp: String?,
        val dnsServers: String?,
        val securityType: WifiSecurityChecker.SecurityType = WifiSecurityChecker.SecurityType.UNKNOWN,
        val securityLabel: String = "",
        val securityAdvice: String = "",
        val signalSamples: Int = 0,
        val signalStdDev: Double = 0.0
    )

    suspend fun evaluateCurrentNetwork(): ThreatResult {
        val evilTwinResult = evilTwinDetector.evaluate()
        val notConnected = "Ponsel tidak terhubung ke WiFi, atau alamat WiFi tidak dapat dibaca."

        if (evilTwinResult == null) {
            return ThreatResult(
                isConnected = false,
                threatLevel = 0,
                bssidChangeScore = 0,
                tlsChangeScore = 0,
                signalInstabilityScore = 0,
                latencyDeviationScore = 0,
                configChangeScore = 0,
                currentSsid = null,
                currentBssid = null,
                bssidDetail = notConnected,
                tlsDetail = notConnected,
                signalDetail = notConnected,
                latencyDetail = notConnected,
                configDetail = notConnected,
                rssi = 0,
                latencyMs = -1L,
                gatewayIp = null,
                dnsServers = null,
                securityType = WifiSecurityChecker.SecurityType.UNKNOWN,
                securityLabel = "",
                securityAdvice = "",
                signalSamples = 0,
                signalStdDev = 0.0
            )
        }

        val ssid = evilTwinResult.currentSsid
        val networkId = baselineStore.getOrCreateNetworkId(ssid)

        val (tlsResult, httpResult) = coroutineScope {
            val tlsDeferred = async { tlsPinningMonitor.evaluate() }
            val httpDeferred = async { httpIntegrityMonitor.evaluate() }
            Pair(tlsDeferred.await(), httpDeferred.await())
        }

        val tlsScoreFinal = maxOf(tlsResult.score, httpResult.score)
        val tlsDetailFinal = "${tlsResult.detail} ${httpResult.detail}"

        val signalResult = signalStabilityMonitor.evaluate()
        val latencyResult = latencyMonitor.evaluate()
        val configResult = networkConfigMonitor.evaluate(networkId)
        val configScore = configResult.configChangeScore
        val securityResult = wifiSecurityChecker.check()

        val threatLevel = FuzzyEngine.computeThreatLevel(
            bssidChangeScore = evilTwinResult.bssidChangeScore,
            tlsChangeScore = tlsScoreFinal,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore,
            configChangeScore = configScore
        )

        val lastEvent = detectionEventDao.getLastEventForNetwork(networkId)
        val currentTime = System.currentTimeMillis()
        val tenMinutes = 10 * 60 * 1000L

        val shouldInsert = if (lastEvent == null) {
            true
        } else {
            val lastLevel = StatusLevel.fromScore(lastEvent.threatLevel)
            val currentLevel = StatusLevel.fromScore(threatLevel)
            val levelChanged = lastLevel != currentLevel
            val scoreChanged = kotlin.math.abs(lastEvent.threatLevel - threatLevel) >= 5
            val timeElapsed = (currentTime - lastEvent.timestamp) > tenMinutes

            levelChanged || scoreChanged || timeElapsed
        }

        if (shouldInsert) {
            detectionEventDao.insertEvent(
                DetectionEventEntity(
                    networkId = networkId,
                    timestamp = currentTime,
                    threatLevel = threatLevel,
                    bssidChangeScore = evilTwinResult.bssidChangeScore,
                    tlsChangeScore = tlsScoreFinal,
                    signalInstabilityScore = signalResult.signalInstabilityScore,
                    latencyDeviationScore = latencyResult.latencyDeviationScore,
                    configChangeScore = configScore
                )
            )
            detectionEventDao.trimHistory()
        }

        return ThreatResult(
            isConnected = true,
            threatLevel = threatLevel,
            bssidChangeScore = evilTwinResult.bssidChangeScore,
            tlsChangeScore = tlsScoreFinal,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore,
            configChangeScore = configScore,
            currentSsid = ssid,
            currentBssid = evilTwinResult.currentBssid,
            bssidDetail = evilTwinResult.detail,
            tlsDetail = tlsDetailFinal,
            signalDetail = signalResult.detail,
            latencyDetail = latencyResult.detail,
            configDetail = configResult.detail,
            rssi = signalResult.currentRssi,
            latencyMs = latencyResult.currentLatencyMs,
            gatewayIp = configResult.gatewayIp,
            dnsServers = configResult.dnsServers,
            securityType = securityResult.type,
            securityLabel = securityResult.label,
            securityAdvice = securityResult.advice,
            signalSamples = signalResult.samplesCount,
            signalStdDev = signalResult.rssiStdDev
        )
    }
}
