package com.example.susnet.detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.Confidence
import com.example.susnet.data.NetworkConfigDao
import com.example.susnet.data.NetworkConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address

class NetworkConfigMonitor(
    private val context: Context,
    private val dao: NetworkConfigDao
) {

    private data class Config(val gatewayIp: String, val dnsServers: String)

    data class Result(
        val available: Boolean,
        val gatewayIp: String?,
        val dnsServers: String?,
        val configChangeScore: Int,
        val detail: String
    )

    suspend fun evaluate(networkId: Long): Result = withContext(Dispatchers.IO) {
        val current = readCurrentConfig()
            ?: return@withContext Result(
                false,
                null,
                null,
                0,
                "Konfigurasi jaringan (gateway dan DNS) tidak dapat dibaca saat ini."
            )

        val known = dao.getForNetwork(networkId)
        val now = System.currentTimeMillis()
        val match = known.firstOrNull {
            it.gatewayIp == current.gatewayIp && it.dnsServers == current.dnsServers
        }
        val dominant = known.maxByOrNull { it.seenCount }

        val gatewayChanged = dominant != null && current.gatewayIp != dominant.gatewayIp
        val dnsChanged = dominant != null && current.dnsServers != dominant.dnsServers

        val confidence = if (match != null) {
            BaselineStore.computeConfidence(match.seenCount, match.firstSeen, now)
        } else {
            Confidence.LOW
        }

        val isDominant = dominant != null && match?.id == dominant.id
        val hasDominant = dominant != null
        val userTrusted = match?.userTrusted ?: false

        val score = computeConfigScore(
            hasDominant = hasDominant,
            isDominant = isDominant,
            confidence = confidence,
            gatewayChanged = gatewayChanged,
            dnsChanged = dnsChanged,
            userTrusted = userTrusted
        )

        val detail = when {
            dominant == null ->
                "Ini pertama kali konfigurasi jaringan ini dicatat. Gateway ${current.gatewayIp}, " +
                        "DNS ${dnsText(current.dnsServers)} disimpan sebagai acuan."

            isDominant ->
                "Gateway ${current.gatewayIp} dan DNS ${dnsText(current.dnsServers)} sama dengan " +
                        "yang biasa dipakai jaringan ini (terlihat ${match.seenCount} kali)."

            else -> {
                val parts = mutableListOf<String>()
                if (gatewayChanged) {
                    parts.add("gateway berubah dari ${dominant.gatewayIp} menjadi ${current.gatewayIp}")
                }
                if (dnsChanged) {
                    parts.add(
                        "server DNS berubah dari ${dnsText(dominant.dnsServers)} " +
                                "menjadi ${dnsText(current.dnsServers)}"
                    )
                }
                val changeStr = if (parts.isNotEmpty()) {
                    "Pada jaringan ini ${parts.joinToString(" dan ")}."
                } else {
                    "Konfigurasi jaringan ini berbeda dari yang biasa."
                }

                if (match != null && (confidence == Confidence.HIGH || userTrusted)) {
                    "$changeStr Gateway ${current.gatewayIp} dan DNS ${dnsText(current.dnsServers)} pernah terlihat ${match.seenCount} kali."
                } else if (match != null) {
                    val ageMs = now - match.firstSeen
                    val days = ageMs / (24 * 60 * 60 * 1000L)
                    val ageStr = if (days < 1) "kurang dari 1 hari" else "$days hari"
                    "$changeStr Konfigurasi ini baru terlihat ${match.seenCount} kali dalam $ageStr. Dipercaya setelah dilihat minimal 5 kali dalam 3 hari."
                } else {
                    "$changeStr Konfigurasi yang biasa sudah terlihat ${dominant.seenCount} kali, sedangkan yang sekarang baru pertama kali."
                }
            }
        }

        if (match != null) {
            val sessionGap = BaselineStore.SESSION_GAP_MS
            val timeSinceLastSeen = now - match.lastSeen
            val newSeenCount = if (timeSinceLastSeen >= sessionGap) match.seenCount + 1 else match.seenCount
            dao.update(match.copy(lastSeen = now, seenCount = newSeenCount))
        } else {
            dao.insert(
                NetworkConfigEntity(
                    networkId = networkId,
                    gatewayIp = current.gatewayIp,
                    dnsServers = current.dnsServers,
                    firstSeen = now,
                    lastSeen = now,
                    seenCount = 1
                )
            )
        }

        Result(true, current.gatewayIp, current.dnsServers, score, detail)
    }

    private fun dnsText(dns: String): String = dns.ifEmpty { "tidak ada" }

    private fun readCurrentConfig(): Config? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val linkProperties = cm.getLinkProperties(network) ?: return null

        val gateway = linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
            ?: return null

        val dns = linkProperties.dnsServers
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .sorted()
            .joinToString(",")

        return Config(gateway, dns)
    }

    companion object {
        fun computeConfigScore(
            hasDominant: Boolean,
            isDominant: Boolean,
            confidence: Confidence,
            gatewayChanged: Boolean,
            dnsChanged: Boolean,
            userTrusted: Boolean = false
        ): Int {
            if (!hasDominant) return 0
            if (isDominant) return 0
            if (userTrusted) return 0

            return if (confidence == Confidence.HIGH) {
                25
            } else {
                when {
                    gatewayChanged && dnsChanged -> 85
                    dnsChanged -> 65
                    else -> 55
                }
            }
        }
    }
}
