package com.example.susnet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detection_events",
    foreignKeys = [
        ForeignKey(
            entity = NetworkEntity::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["networkId"]), Index(value = ["timestamp"])]
)
data class DetectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkId: Long,
    val timestamp: Long,
    val threatLevel: Int,
    val bssidChangeScore: Int,
    val tlsChangeScore: Int,
    val signalInstabilityScore: Int,
    val latencyDeviationScore: Int,
    val configChangeScore: Int = 0
)
