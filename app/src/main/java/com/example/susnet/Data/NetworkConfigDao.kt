package com.example.susnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface NetworkConfigDao {

    @Query("SELECT * FROM network_configs WHERE networkId = :networkId ORDER BY firstSeen ASC")
    suspend fun getForNetwork(networkId: Long): List<NetworkConfigEntity>

    @Insert
    suspend fun insert(config: NetworkConfigEntity)

    @Update
    suspend fun update(config: NetworkConfigEntity)

    @Query("UPDATE network_configs SET userTrusted = :trusted WHERE networkId = :networkId AND gatewayIp = :gatewayIp AND dnsServers = :dnsServers")
    suspend fun setUserTrustedForConfig(networkId: Long, gatewayIp: String, dnsServers: String, trusted: Boolean)

    @Query("UPDATE network_configs SET userTrusted = 0 WHERE networkId = :networkId")
    suspend fun revokeUserTrustedForNetworkConfigs(networkId: Long)
}
