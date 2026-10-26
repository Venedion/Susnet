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
        val ssid = connectionInfo.ssid?.trim('"')
        val currentBssid = connectionInfo.bssid
        if (!isReadable(ssid, currentBssid)) return null

        val cleanSsid = ssid!!
        val cleanBssid = currentBssid!!

        val knownBssids = baselineStore.getKnownBssids(cleanSsid)
        val isBrandNewNetwork = knownBssids.isEmpty()
        val isKnownBssid = cleanBssid in knownBssids

        baselineStore.registerSighting(cleanSsid, cleanBssid)
        val confidence = baselineStore.getConfidence(cleanSsid, cleanBssid)

        val ap = baselineStore.getAccessPointDetails(cleanSsid, cleanBssid)
        val seenCount = ap?.seenCount ?: 1
        val firstSeen = ap?.firstSeen ?: System.currentTimeMillis()
        val ageMs = System.currentTimeMillis() - firstSeen
        val days = ageMs / (24 * 60 * 60 * 1000L)
        val ageStr = if (days < 1) "kurang dari 1 hari" else "$days hari"

        val userTrusted = ap?.userTrusted ?: false
        val score = bssidScore(isBrandNewNetwork, isKnownBssid, confidence, userTrusted)

        val detail = when {
            isBrandNewNetwork ->
                "Ini pertama kali kamu tersambung ke \"$cleanSsid\", jadi belum ada pembanding. " +
                        "Alamat perangkat WiFi (BSSID) $cleanBssid dicatat sebagai acuan."

            isKnownBssid -> {
                if (confidence == Confidence.HIGH || userTrusted) {
                    "BSSID $cleanBssid sudah pernah terlihat pada \"$cleanSsid\" (${confidenceLabel(confidence)})."
                } else {
                    "Alamat ini baru terlihat $seenCount kali dalam $ageStr. Dipercaya setelah dilihat minimal 5 kali dalam 3 hari."
                }
            }

            else ->
                "BSSID $cleanBssid belum pernah terlihat pada \"$cleanSsid\". " +
                        "BSSID yang dikenal untuk nama ini: ${knownBssids.joinToString(", ")}. " +
                        "Nama sama dengan perangkat berbeda bisa berarti jaringan tiruan, " +
                        "atau router atau repeater lain milik jaringan yang sama."
        }

        return Result(score, cleanSsid, cleanBssid, confidence, detail)
    }

    private fun confidenceLabel(confidence: Confidence): String = when (confidence) {
        Confidence.HIGH -> "sudah sangat dikenal"
        Confidence.MEDIUM -> "cukup dikenal"
        Confidence.LOW -> "baru beberapa kali terlihat, belum sepenuhnya dipercaya"
    }

    companion object {
        fun isReadable(ssid: String?, bssid: String?): Boolean {
            if (ssid.isNullOrBlank() || ssid.trim().equals("<unknown ssid>", ignoreCase = true)) return false
            if (bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00") return false
            return true
        }

        fun bssidScore(
            isBrandNewNetwork: Boolean,
            isKnownBssid: Boolean,
            confidence: Confidence,
            userTrusted: Boolean = false
        ): Int {
            return when {
                isBrandNewNetwork -> 0
                isKnownBssid && userTrusted -> 0
                isKnownBssid -> when (confidence) {
                    Confidence.HIGH -> 0
                    Confidence.MEDIUM -> 40
                    Confidence.LOW -> 40
                }
                else -> 60
            }
        }
    }
}
