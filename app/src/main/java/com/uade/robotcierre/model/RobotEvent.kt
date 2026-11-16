package com.uade.robotcierre.model

enum class EventType {
    PERSON_DETECTED,
    ZONE_NOT_VERIFIABLE,
    LOW_BATTERY,
    CONNECTION_LOST,
    OBSTACLE,
    MISSION_COMPLETE
}

enum class Severity { INFO, WARNING, CRITICAL }

/** Evento o alerta generado por el robot. */
data class RobotEvent(
    val eventId: String,
    val type: EventType,
    val severity: Severity,
    val zone: Zone?,
    val details: String,
    val acknowledged: Boolean = false
)
