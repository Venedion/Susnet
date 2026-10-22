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
        val confidence: Confidence,
        val detail: String
    )

    suspend fun evaluate(): Result? {
        val connectionInfo = wifiManager.connectionInfo
        val ssid = connectionInfo.ssid?.trim('"') ?: return null
        val currentBssid = connectionInfo.bssid ?: return null

        val knownBssids = baselineStore.getKnownBssids(ssid)
        val isBrandNewNetwork = knownBssids.isEmpty()
        val isKnownBssid = currentBssid in knownBssids

        baselineStore.registerSighting(ssid, currentBssid)
        val confidence = baselineStore.getConfidence(ssid, currentBssid)

        val score = when {
            isBrandNewNetwork -> 0
            isKnownBssid -> when (confidence) {
                Confidence.HIGH -> 0
                Confidence.MEDIUM -> 20
                Confidence.LOW -> 40
            }
            else -> 60
        }

        val detail = when {
            isBrandNewNetwork ->
                "Ini pertama kali kamu tersambung ke \"$ssid\", jadi belum ada pembanding. " +
                        "Alamat perangkat WiFi (BSSID) $currentBssid dicatat sebagai acuan."

            isKnownBssid ->
                "BSSID $currentBssid sudah pernah terlihat pada \"$ssid\" (${confidenceLabel(confidence)})."

            else ->
                "BSSID $currentBssid belum pernah terlihat pada \"$ssid\". " +
                        "BSSID yang dikenal untuk nama ini: ${knownBssids.joinToString(", ")}. " +
                        "Nama sama dengan perangkat berbeda bisa berarti jaringan tiruan, " +
                        "atau router atau repeater lain milik jaringan yang sama."
        }

        return Result(score, ssid, currentBssid, confidence, detail)
    }

    private fun confidenceLabel(confidence: Confidence): String = when (confidence) {
        Confidence.HIGH -> "sudah sangat dikenal"
        Confidence.MEDIUM -> "cukup dikenal"
        Confidence.LOW -> "baru beberapa kali terlihat, belum sepenuhnya dipercaya"
    }
}