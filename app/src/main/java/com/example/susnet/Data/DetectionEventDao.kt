package com.example.susnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DetectionEventDao {

    @Insert
    suspend fun insertEvent(event: DetectionEventEntity)

    @Query("SELECT * FROM detection_events WHERE networkId = :networkId ORDER BY timestamp DESC")
    suspend fun getEventsForNetwork(networkId: Long): List<DetectionEventEntity>

    @Query("SELECT * FROM detection_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<DetectionEventEntity>

    @Query("""
        SELECT COUNT(*) FROM detection_events
        WHERE networkId = :networkId AND threatLevel >= :threshold AND timestamp >= :since
    """)
    suspend fun countHighThreatEvents(networkId: Long, threshold: Int, since: Long): Int
}
