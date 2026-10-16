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
        return when {
            accessPoint.seenCount >= HIGH_THRESHOLD -> Confidence.HIGH
            accessPoint.seenCount >= MEDIUM_THRESHOLD -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
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

    suspend fun forceHighConfidence(ssid: String, bssid: String) {
        val now = System.currentTimeMillis()
        val network = findOrCreateNetwork(ssid)
        val existing = dao.findAccessPoint(network.id, bssid)
        if (existing == null) {
            dao.insertAccessPoint(
                AccessPointEntity(networkId = network.id, bssid = bssid, firstSeen = now, lastSeen = now, seenCount = HIGH_THRESHOLD)
            )
        } else {
            dao.updateAccessPoint(existing.copy(lastSeen = now, seenCount = HIGH_THRESHOLD))
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
        private const val SESSION_GAP_MS = 30 * 60 * 1000L
        private const val MEDIUM_THRESHOLD = 2
        private const val HIGH_THRESHOLD = 5
    }
}
