package com.uade.robotcierre.repository

import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotEvent
import kotlinx.coroutines.flow.Flow

/** Historial persistente de rondas y eventos. Lo implementa Room (`data/`). */
interface HistoryRepository {
    /** Rondas, de la más nueva a la más vieja. */
    val missions: Flow<List<MissionRecord>>

    /** Eventos que ocurrieron fuera de una ronda, del más nuevo al más viejo. */
    val eventsOutsideMissions: Flow<List<EventRecord>>

    /** Eventos de una ronda, en orden cronológico. */
    fun eventsOf(missionId: Long): Flow<List<EventRecord>>

    /** Mayor número de código (`M-007` → 7) entre las rondas guardadas; 0 si no hay ninguna. */
    suspend fun lastMissionNumber(): Int

    /** Abre una ronda en curso y devuelve su id. */
    suspend fun openMission(code: String, startedAt: Long): Long

    suspend fun closeMission(id: Long, endedAt: Long, result: MissionResult, zonesVerified: Int)

    suspend fun updateZonesVerified(id: Long, zonesVerified: Int)

    /** Guarda un evento (con su ronda, o null si ocurrió fuera de una) y devuelve su id. */
    suspend fun insertEvent(missionId: Long?, event: RobotEvent, timestamp: Long): Long

    suspend fun markEventAcknowledged(id: Long)

    /** Cierra como INTERRUPTED las rondas que quedaron abiertas (la app se cerró en medio). */
    suspend fun closeInterruptedMissions(now: Long)
}
