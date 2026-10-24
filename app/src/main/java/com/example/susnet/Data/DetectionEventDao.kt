package com.example.susnet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DetectionEventDao {

    @Insert
    suspend fun insertEvent(event: DetectionEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DetectionEventEntity)

    @Query("DELETE FROM detection_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM detection_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM detection_events WHERE networkId = :networkId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEventForNetwork(networkId: Long): DetectionEventEntity?

    @Query("DELETE FROM detection_events WHERE id NOT IN (SELECT id FROM detection_events ORDER BY timestamp DESC LIMIT 100)")
    suspend fun trimHistory()

    @Query("SELECT * FROM detection_events WHERE networkId = :networkId ORDER BY timestamp DESC")
    suspend fun getEventsForNetwork(networkId: Long): List<DetectionEventEntity>

    @Query("SELECT * FROM detection_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<DetectionEventEntity>

    @Query("""
        SELECT e.*, n.ssid AS ssid
        FROM detection_events e
        LEFT JOIN networks n ON n.id = e.networkId
        ORDER BY e.timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentHistoryItems(limit: Int): List<HistoryItem>

    @Query("""
        SELECT COUNT(*) FROM detection_events
        WHERE networkId = :networkId AND threatLevel >= :threshold AND timestamp >= :since
    """)
    suspend fun countHighThreatEvents(networkId: Long, threshold: Int, since: Long): Int
}
