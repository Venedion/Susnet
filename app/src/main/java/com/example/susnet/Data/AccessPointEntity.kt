package com.example.susnet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "access_points",
    foreignKeys = [
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["networkId", "bssid"], unique = true)]
)
data class AccessPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkId: Long,
    val bssid: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val seenCount: Int
)
