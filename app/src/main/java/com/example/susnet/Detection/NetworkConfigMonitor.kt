package com.example.susnet.detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

        val score = when {
            dominant == null -> 0
            match == null -> when {
                gatewayChanged && dnsChanged -> 85
                dnsChanged -> 65
                else -> 55
            }
            match.id == dominant.id -> 0
            else -> 25
        }

        val detail = when {
            dominant == null ->
                "Ini pertama kali konfigurasi jaringan ini dicatat. Gateway ${current.gatewayIp}, " +
                        "DNS ${dnsText(current.dnsServers)} disimpan sebagai acuan."

            match == null -> {
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
                "Pada jaringan ini ${parts.joinToString(" dan ")}. " +
                        "Konfigurasi yang biasa sudah terlihat ${dominant.seenCount} kali, " +
                        "sedangkan yang sekarang baru pertama kali."
            }

            match.id == dominant.id ->
                "Gateway ${current.gatewayIp} dan DNS ${dnsText(current.dnsServers)} sama dengan " +
                        "yang biasa dipakai jaringan ini (terlihat ${match.seenCount} kali)."

            else ->
                "Gateway ${current.gatewayIp} dan DNS ${dnsText(current.dnsServers)} pernah terlihat " +
                        "${match.seenCount} kali, tetapi bukan konfigurasi yang paling sering dipakai " +
                        "jaringan ini (${dominant.gatewayIp}, DNS ${dnsText(dominant.dnsServers)})."
        }

        if (match != null) {
            dao.update(match.copy(lastSeen = now, seenCount = match.seenCount + 1))
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
}