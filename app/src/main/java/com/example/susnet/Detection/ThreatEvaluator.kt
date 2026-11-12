package com.example.susnet.detection

import com.example.susnet.StatusLevel
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.DetectionEventDao
import com.example.susnet.data.DetectionEventEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val evalMutex = Mutex()

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
        val signalStdDev: Double = 0.0,
        val securityDowngrade: Boolean = false,
        val downgradeScore: Int = 0,
        val tlsCardText: String = "",
        val tlsOutcome: TlsPinningMonitor.Outcome = TlsPinningMonitor.Outcome.INCONCLUSIVE,
        val httpOutcome: HttpIntegrityMonitor.Outcome = HttpIntegrityMonitor.Outcome.INCONCLUSIVE
    )

    suspend fun evaluateCurrentNetwork(): ThreatResult = evalMutex.withLock {
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
                signalStdDev = 0.0,
                tlsCardText = "",
                tlsOutcome = TlsPinningMonitor.Outcome.INCONCLUSIVE,
                httpOutcome = HttpIntegrityMonitor.Outcome.INCONCLUSIVE
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

        val baselineSecurity = baselineStore.getSecurityBaseline(ssid)
        val currentSecurity = securityResult.type
        val downgradeScore = SecurityDowngrade.downgradeScore(baselineSecurity, currentSecurity)
        val nextBaseline = SecurityDowngrade.nextBaseline(baselineSecurity, currentSecurity)
        if (nextBaseline != null) {
            baselineStore.saveSecurityBaseline(ssid, nextBaseline)
        }

        val bssidScoreFinal = maxOf(evilTwinResult.bssidChangeScore, downgradeScore)
        val isDowngrade = downgradeScore > 0
        val bssidDetailFinal = if (isDowngrade) {
            "${evilTwinResult.detail} Jaringan \"$ssid\" sebelumnya terlihat memakai keamanan yang lebih kuat, sekarang ${securityResult.label}. Ini tanda khas jaringan tiruan, tetapi bisa juga karena pengaturan router diubah."
        } else {
            evilTwinResult.detail
        }

        val threatLevel = FuzzyEngine.computeThreatLevel(
            bssidChangeScore = bssidScoreFinal,
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
                    bssidChangeScore = bssidScoreFinal,
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
            bssidChangeScore = bssidScoreFinal,
            tlsChangeScore = tlsScoreFinal,
            signalInstabilityScore = signalResult.signalInstabilityScore,
            latencyDeviationScore = latencyResult.latencyDeviationScore,
            configChangeScore = configScore,
            currentSsid = ssid,
            currentBssid = evilTwinResult.currentBssid,
            bssidDetail = bssidDetailFinal,
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
            signalStdDev = signalResult.rssiStdDev,
            securityDowngrade = isDowngrade,
            downgradeScore = downgradeScore,
            tlsCardText = tlsCardSummary(tlsResult.outcome, httpResult.outcome),
            tlsOutcome = tlsResult.outcome,
            httpOutcome = httpResult.outcome
        )
    }

    companion object {
        fun tlsCardSummary(
            tls: TlsPinningMonitor.Outcome,
            http: HttpIntegrityMonitor.Outcome
        ): String {
            return when {
                tls == TlsPinningMonitor.Outcome.CERT_FAILURE && http == HttpIntegrityMonitor.Outcome.INTERCEPTED ->
                    "Sertifikat tidak cocok dan HTTP dialihkan"
                tls == TlsPinningMonitor.Outcome.CERT_FAILURE ->
                    "Sertifikat tidak cocok"
                http == HttpIntegrityMonitor.Outcome.INTERCEPTED ->
                    "Lalu lintas HTTP dialihkan"
                tls == TlsPinningMonitor.Outcome.VALID && http == HttpIntegrityMonitor.Outcome.CLEAN ->
                    "Pin cocok, HTTP aman"
                tls == TlsPinningMonitor.Outcome.VALID && http == HttpIntegrityMonitor.Outcome.INCONCLUSIVE ->
                    "Pin cocok, HTTP tidak terperiksa"
                tls == TlsPinningMonitor.Outcome.INCONCLUSIVE && http == HttpIntegrityMonitor.Outcome.CLEAN ->
                    "HTTP aman, sertifikat tidak terperiksa"
                else ->
                    "Tidak dapat diperiksa saat ini"
            }
        }
    }
}
