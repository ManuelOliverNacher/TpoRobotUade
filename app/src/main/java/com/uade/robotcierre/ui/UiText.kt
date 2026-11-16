package com.uade.robotcierre.ui

import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.ZoneStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Textos en español para los valores del modelo (los identificadores del código están en inglés). */

fun RobotState.label() = when (this) {
    RobotState.IDLE -> "Reposo"
    RobotState.ON_MISSION -> "En ronda"
    RobotState.PAUSED -> "Pausado"
    RobotState.RETURNING -> "Volviendo"
    RobotState.STOPPED -> "Detenido"
    RobotState.FAULT -> "Falla"
}

fun ConnectionState.label() = when (this) {
    ConnectionState.CONNECTED -> "Conectado"
    ConnectionState.DEGRADED -> "Degradada"
    ConnectionState.DISCONNECTED -> "Desconectado"
}

fun LocalizationQuality.label() = when (this) {
    LocalizationQuality.GOOD -> "Buena"
    LocalizationQuality.DEGRADED -> "Degradada"
}

fun CommandType.label() = when (this) {
    CommandType.START_MISSION -> "Iniciar ronda"
    CommandType.PAUSE_MISSION -> "Pausar ronda"
    CommandType.RESUME_MISSION -> "Reanudar ronda"
    CommandType.CANCEL_MISSION -> "Cancelar ronda"
    CommandType.RETURN_TO_SAFE_POINT -> "Volver a la base"
    CommandType.OPERATIONAL_STOP -> "Detención operativa"
    CommandType.ACK_ALERT -> "Reconocer alertas"
    CommandType.MANUAL_REVIEW -> "Revisión manual"
}

fun EventType.label() = when (this) {
    EventType.PERSON_DETECTED -> "Persona detectada"
    EventType.ZONE_NOT_VERIFIABLE -> "Zona no verificable"
    EventType.LOW_BATTERY -> "Batería baja"
    EventType.CONNECTION_LOST -> "Conexión perdida"
    EventType.OBSTACLE -> "Obstáculo"
    EventType.MISSION_COMPLETE -> "Ronda completa"
}

fun Severity.label() = when (this) {
    Severity.INFO -> "Info"
    Severity.WARNING -> "Advertencia"
    Severity.CRITICAL -> "Crítica"
}

fun ZoneStatus.label() = when (this) {
    ZoneStatus.PENDING -> "Pendiente"
    ZoneStatus.FREE -> "Libre"
    ZoneStatus.PERSON_FOUND -> "Persona encontrada"
    ZoneStatus.NOT_VERIFIABLE -> "No verificable"
    ZoneStatus.MANUAL_REVIEW -> "Revisión manual"
    ZoneStatus.VERIFIED_MANUALLY -> "Verificada manualmente"
}

fun CommandStatus.label() = when (this) {
    CommandStatus.PENDING -> "Pendiente"
    CommandStatus.CONFIRMED -> "Confirmada"
    CommandStatus.REJECTED -> "Rechazada"
    CommandStatus.NO_RESPONSE -> "Sin respuesta"
}

fun MissionResult.label() = when (this) {
    MissionResult.IN_PROGRESS -> "En curso"
    MissionResult.COMPLETED -> "Completa"
    MissionResult.CANCELLED -> "Cancelada"
    MissionResult.RETURNED_EARLY -> "Volvió antes de terminar"
    MissionResult.INTERRUPTED -> "Interrumpida"
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateTimeFormat =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

/** Hora local, por ejemplo "22:41:05". */
fun formatTime(epochMillis: Long): String = timeFormat.format(Instant.ofEpochMilli(epochMillis))

/** Fecha y hora local, por ejemplo "20/11/2026 22:41". */
fun formatDateTime(epochMillis: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(epochMillis))
