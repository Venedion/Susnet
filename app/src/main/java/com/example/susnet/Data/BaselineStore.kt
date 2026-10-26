package com.example.susnet.data

import android.content.Context

enum class Confidence { LOW, MEDIUM, HIGH }

class BaselineStore(private val context: Context) {

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

    suspend fun trustCurrent(ssid: String, bssid: String, gateway: String, dns: String) {
        val now = System.currentTimeMillis()
        val network = findOrCreateNetwork(ssid)

        val ap = dao.findAccessPoint(network.id, bssid)
        if (ap == null) {
            dao.insertAccessPoint(
                AccessPointEntity(networkId = network.id, bssid = bssid, firstSeen = now, lastSeen = now, seenCount = 1, userTrusted = true)
            )
        } else {
            dao.setUserTrustedForAccessPoint(network.id, bssid, true)
        }

        val configDao = SusnetDatabase.getInstance(context).networkConfigDao()
        val configs = configDao.getForNetwork(network.id)
        val matchingConfig = configs.firstOrNull { it.gatewayIp == gateway && it.dnsServers == dns }
        if (matchingConfig == null) {
            configDao.insert(
                NetworkConfigEntity(networkId = network.id, gatewayIp = gateway, dnsServers = dns, firstSeen = now, lastSeen = now, seenCount = 1, userTrusted = true)
            )
        } else {
            configDao.setUserTrustedForConfig(network.id, gateway, dns, true)
        }
    }

    suspend fun revokeTrust(ssid: String) {
        val network = dao.findNetwork(ssid) ?: return
        dao.revokeUserTrustedForNetworkAccessPoints(network.id)
        val configDao = SusnetDatabase.getInstance(context).networkConfigDao()
        configDao.revokeUserTrustedForNetworkConfigs(network.id)
    }

    suspend fun isCurrentTrusted(ssid: String, bssid: String, gateway: String, dns: String): Boolean {
        val network = dao.findNetwork(ssid) ?: return false
        val ap = dao.findAccessPoint(network.id, bssid) ?: return false
        if (!ap.userTrusted) return false
        val configDao = SusnetDatabase.getInstance(context).networkConfigDao()
        val configs = configDao.getForNetwork(network.id)
        val matchingConfig = configs.firstOrNull { it.gatewayIp == gateway && it.dnsServers == dns }
        return matchingConfig?.userTrusted == true
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
