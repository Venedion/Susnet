package com.example.susnet.notification

object AutoCheckPolicy {

    fun isBssidInvalid(bssid: String?): Boolean {
        return bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00"
    }

    fun shouldRun(
        lastBssid: String?,
        currentBssid: String?,
        lastCheckAt: Long,
        now: Long,
        minGapMs: Long = 5 * 60 * 1000L
    ): Boolean {
        if (isBssidInvalid(currentBssid)) return false
        if (lastBssid == null) return true
        if (currentBssid != lastBssid) return true
        return (now - lastCheckAt) >= minGapMs
    }
}
