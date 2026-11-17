package com.uade.robotcierre.data

import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.MissionCode
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Historial respaldado por Room. */
class RoomHistoryRepository(private val dao: HistoryDao) : HistoryRepository {

    override val missions: Flow<List<MissionRecord>> =
        dao.observeMissions().map { list -> list.map { it.toRecord() } }

    override val eventsOutsideMissions: Flow<List<EventRecord>> =
        dao.observeEventsOutsideMissions().map { list -> list.map { it.toRecord() } }

    override fun eventsOf(missionId: Long): Flow<List<EventRecord>> =
        dao.observeEvents(missionId).map { list -> list.map { it.toRecord() } }

    override suspend fun lastMissionNumber(): Int = MissionCode.lastNumber(dao.allMissionCodes())

    override suspend fun openMission(code: String, startedAt: Long): Long =
        dao.insertMission(
            MissionEntity(
                code = code,
                startedAt = startedAt,
                endedAt = null,
                result = MissionResult.IN_PROGRESS,
                zonesVerified = 0
            )
        )

    override suspend fun closeMission(id: Long, endedAt: Long, result: MissionResult, zonesVerified: Int) =
        dao.closeMission(id, endedAt, result, zonesVerified)

    override suspend fun updateZonesVerified(id: Long, zonesVerified: Int) =
        dao.updateZonesVerified(id, zonesVerified)

    override suspend fun insertEvent(missionId: Long?, event: RobotEvent, timestamp: Long): Long =
        dao.insertEvent(
            EventEntity(
                missionId = missionId,
                type = event.type,
                severity = event.severity,
                zone = event.zone,
                details = event.details,
                timestamp = timestamp,
                acknowledged = event.acknowledged
            )
        )

    override suspend fun markEventAcknowledged(id: Long) = dao.markEventAcknowledged(id)

    override suspend fun closeInterruptedMissions(now: Long) =
        dao.closeInterrupted(now, MissionResult.INTERRUPTED, MissionResult.IN_PROGRESS)
}
