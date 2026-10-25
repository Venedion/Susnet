package com.example.susnet.data

import android.content.Context

enum class Confidence { LOW, MEDIUM, HIGH }

class BaselineStore(context: Context) {

    private val dao = SusnetDatabase.getInstance(context).baselineDao()

    suspend fun getOrCreateNetworkId(ssid: String): Long {
        return findOrCreateNetwork(ssid).id
    }

    suspend fun getKnownBssids(ssid: String): Set<String> {
        val network = dao.findNetwork(ssid) ?: return emptySet()
        return dao.getBssidsForNetwork(network.id).toSet()
    }

    suspend fun getConfidence(ssid: String, bssid: String): Confidence {
        val network = dao.findNetwork(ssid) ?: return Confidence.LOW
        val accessPoint = dao.findAccessPoint(network.id, bssid) ?: return Confidence.LOW
        val now = System.currentTimeMillis()
        return computeConfidence(accessPoint.seenCount, accessPoint.firstSeen, now)
    }

    suspend fun getAccessPointDetails(ssid: String, bssid: String): AccessPointEntity? {
        val network = dao.findNetwork(ssid) ?: return null
        return dao.findAccessPoint(network.id, bssid)
    }

    suspend fun registerSighting(ssid: String, bssid: String) {
        val now = System.currentTimeMillis()
        val network = findOrCreateNetwork(ssid)
        val accessPoint = dao.findAccessPoint(network.id, bssid)

        when {
            accessPoint == null -> dao.insertAccessPoint(
                AccessPointEntity(networkId = network.id, bssid = bssid, firstSeen = now, lastSeen = now, seenCount = 1)
            )
            now - accessPoint.lastSeen >= SESSION_GAP_MS -> dao.updateAccessPoint(
                accessPoint.copy(lastSeen = now, seenCount = accessPoint.seenCount + 1)
            )
            else -> dao.updateAccessPoint(accessPoint.copy(lastSeen = now))
        }
    }

    private suspend fun findOrCreateNetwork(ssid: String): NetworkEntity {
        val now = System.currentTimeMillis()
        return dao.findNetwork(ssid) ?: run {
            val id = dao.insertNetwork(NetworkEntity(ssid = ssid, lastSeen = now))
            NetworkEntity(id = id, ssid = ssid, lastSeen = now)
        }
    }

    companion object {
        const val SESSION_GAP_MS = 30 * 60 * 1000L
        const val MEDIUM_THRESHOLD = 2
        const val HIGH_THRESHOLD = 5
        const val MIN_TRUST_AGE_MS = 3 * 24 * 60 * 60 * 1000L

        fun computeConfidence(seenCount: Int, firstSeen: Long, now: Long = System.currentTimeMillis()): Confidence {
            val age = now - firstSeen
            return when {
                seenCount >= HIGH_THRESHOLD && age >= MIN_TRUST_AGE_MS -> Confidence.HIGH
                seenCount >= MEDIUM_THRESHOLD -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
        }
    }
}
