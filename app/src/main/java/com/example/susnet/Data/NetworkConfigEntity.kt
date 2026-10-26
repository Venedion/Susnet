package com.example.susnet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "network_configs",
    foreignKeys = [
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["networkId", "gatewayIp", "dnsServers"], unique = true)]
)
data class NetworkConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkId: Long,
    val gatewayIp: String,
    val dnsServers: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val seenCount: Int,
    val userTrusted: Boolean = false
)
