package com.example.susnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface BaselineDao {

    @Query("SELECT * FROM networks WHERE ssid = :ssid LIMIT 1")
    suspend fun findNetwork(ssid: String): NetworkEntity?

    @Insert
    suspend fun insertNetwork(network: NetworkEntity): Long

    @Query("SELECT * FROM access_points WHERE networkId = :networkId AND bssid = :bssid LIMIT 1")
    suspend fun findAccessPoint(networkId: Long, bssid: String): AccessPointEntity?

    @Insert
    suspend fun insertAccessPoint(accessPoint: AccessPointEntity)

    @Update
    suspend fun updateAccessPoint(accessPoint: AccessPointEntity)

    @Query("SELECT COUNT(*) FROM access_points WHERE networkId = :networkId")
    suspend fun countAccessPoints(networkId: Long): Int

    @Query("SELECT bssid FROM access_points WHERE networkId = :networkId")
    suspend fun getBssidsForNetwork(networkId: Long): List<String>
}
