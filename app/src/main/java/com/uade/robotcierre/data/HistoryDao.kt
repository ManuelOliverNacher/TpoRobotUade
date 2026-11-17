package com.uade.robotcierre.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uade.robotcierre.model.MissionResult
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertMission(mission: MissionEntity): Long

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Query("UPDATE missions SET endedAt = :endedAt, result = :result, zonesVerified = :zonesVerified WHERE id = :id")
    suspend fun closeMission(id: Long, endedAt: Long, result: MissionResult, zonesVerified: Int)

    @Query("UPDATE missions SET zonesVerified = :zonesVerified WHERE id = :id")
    suspend fun updateZonesVerified(id: Long, zonesVerified: Int)

    @Query("UPDATE events SET acknowledged = 1 WHERE id = :id")
    suspend fun markEventAcknowledged(id: Long)

    @Query("UPDATE missions SET endedAt = :now, result = :interrupted WHERE result = :inProgress")
    suspend fun closeInterrupted(now: Long, interrupted: MissionResult, inProgress: MissionResult)

    @Query("SELECT code FROM missions")
    suspend fun allMissionCodes(): List<String>

    @Query("SELECT * FROM missions ORDER BY startedAt DESC, id DESC")
    fun observeMissions(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM events WHERE missionId = :missionId ORDER BY timestamp ASC, id ASC")
    fun observeEvents(missionId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE missionId IS NULL ORDER BY timestamp DESC, id DESC")
    fun observeEventsOutsideMissions(): Flow<List<EventEntity>>
}
