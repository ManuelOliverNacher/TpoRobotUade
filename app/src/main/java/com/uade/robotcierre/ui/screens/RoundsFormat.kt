package com.uade.robotcierre.ui.screens

import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.components.Tone

/** Resumen de cierre de la última ronda (texto + zonas que esperan revisión manual). */
data class RoundSummary(
    val title: String,
    val lines: List<String>,
    val reviewZones: List<Zone>,
    val tone: Tone
)

/**
 * Arma el resumen de cierre. Es null mientras hay una ronda en curso, o si el robot todavía no
 * recorrió nada (todas las zonas pendientes): los estados de zona persisten hasta la próxima ronda.
 * [lastResult] es el resultado de la ronda más reciente del historial.
 */
fun roundSummary(t: Telemetry?, lastResult: MissionResult?): RoundSummary? {
    if (t == null || lastResult == null || lastResult == MissionResult.IN_PROGRESS) return null
    if (t.missionId != null || t.state != RobotState.IDLE) return null
    if (t.zonesStatus.values.all { it == ZoneStatus.PENDING }) return null

    fun zonesWith(vararg s: ZoneStatus) = Zone.entries.filter { t.zonesStatus[it] in s }
    val free = zonesWith(ZoneStatus.FREE, ZoneStatus.VERIFIED_MANUALLY).size
    val review = zonesWith(ZoneStatus.MANUAL_REVIEW, ZoneStatus.NOT_VERIFIABLE)
    val people = zonesWith(ZoneStatus.PERSON_FOUND)
    val pending = zonesWith(ZoneStatus.PENDING)

    val title = when (lastResult) {
        MissionResult.COMPLETED -> "Ronda completa."
        MissionResult.CANCELLED -> "Ronda cancelada."
        MissionResult.RETURNED_EARLY -> "Ronda incompleta: el robot volvió a la base antes de terminar."
        MissionResult.INTERRUPTED -> "Ronda interrumpida."
        MissionResult.IN_PROGRESS -> return null
    }
    val lines = buildList {
        add("Zonas libres: $free de ${Zone.TOTAL}.")
        review.forEach { add("${it.label}: requiere revisión manual.") }
        people.forEach { add("${it.label}: se encontró una persona.") }
        if (pending.isNotEmpty()) add("Zonas pendientes: ${pending.joinToString(", ") { it.label }}.")
    }
    return RoundSummary(
        title = title,
        lines = lines,
        reviewZones = review,
        // Verde solo si no queda nada por hacer: sin personas encontradas ni zonas por revisar.
        tone = if (lastResult == MissionResult.COMPLETED && people.isEmpty() && review.isEmpty()) {
            Tone.OK
        } else {
            Tone.WARNING
        }
    )
}
