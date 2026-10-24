package com.example.susnet.data

import androidx.room.Embedded

data class HistoryItem(
    @Embedded val event: DetectionEventEntity,
    val ssid: String?
)
