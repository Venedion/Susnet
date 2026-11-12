package com.example.susnet.detection

class RssiWindow(private val windowSize: Int = 10) {

    private val samplesDeque = ArrayDeque<Int>()
    private var lastBssid: String? = null

    @Synchronized
    fun add(bssid: String?, rssi: Int) {
        if (bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00") {
            samplesDeque.clear()
            lastBssid = null
            return
        }

        if (bssid != lastBssid) {
            samplesDeque.clear()
            lastBssid = bssid
            samplesDeque.addLast(rssi)
            return
        }

        samplesDeque.addLast(rssi)
        if (samplesDeque.size > windowSize) {
            samplesDeque.removeFirst()
        }
    }

    @Synchronized
    fun samples(): List<Int> {
        return samplesDeque.toList()
    }
}
