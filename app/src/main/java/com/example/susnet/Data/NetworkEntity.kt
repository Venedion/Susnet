package com.example.susnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "networks", indices = [Index(value = ["ssid"], unique = true)])
data class NetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val lastSeen: Long
)
