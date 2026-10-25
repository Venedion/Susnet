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
}