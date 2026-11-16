package com.uade.robotcierre.model

/**
 * Ronda del historial (lo que ve la UI; no es la entidad de Room).
 * @property code código que mostró el robot ("M-001"); NO es único entre sesiones.
 * @property id identificador único de la base de datos.
 */
data class MissionRecord(
    val id: Long,
    val code: String,
    val startedAt: Long,
    val endedAt: Long?,
    val result: MissionResult,
    val zonesVerified: Int,
    val zonesTotal: Int = Zone.TOTAL
)

/** Evento del historial. [missionId] es null si ocurrió fuera de una ronda. */
data class EventRecord(
    val id: Long,
    val missionId: Long?,
    val type: EventType,
    val severity: Severity,
    val zone: Zone?,
    val details: String,
    val timestamp: Long,
    val acknowledged: Boolean
)
