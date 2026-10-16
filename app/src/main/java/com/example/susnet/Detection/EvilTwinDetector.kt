package com.example.susnet.detection

import android.net.wifi.WifiManager
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.Confidence

class EvilTwinDetector(
    private val wifiManager: WifiManager,
    private val baselineStore: BaselineStore
) {

    data class Result(
        val bssidChangeScore: Int,
        val currentSsid: String,
        val currentBssid: String,
        val confidence: Confidence
    )

    suspend fun evaluate(): Result? {
        val connectionInfo = wifiManager.connectionInfo
        val ssid = connectionInfo.ssid?.trim('"') ?: return null
        val currentBssid = connectionInfo.bssid ?: return null

        val knownBssids = baselineStore.getKnownBssids(ssid)
        val isBrandNewNetwork = knownBssids.isEmpty()
        val isKnownBssid = currentBssid in knownBssids

        baselineStore.registerSighting(ssid, currentBssid)

        val score = when {
            isBrandNewNetwork -> 0
            isKnownBssid -> when (baselineStore.getConfidence(ssid, currentBssid)) {
                Confidence.HIGH -> 0
                Confidence.MEDIUM -> 20
                Confidence.LOW -> 40
            }
            else -> 60
        }

        return Result(score, ssid, currentBssid, baselineStore.getConfidence(ssid, currentBssid))
    }
}
