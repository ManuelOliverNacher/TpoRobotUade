package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus

/**
 * Datos que usan las reglas.
 * @property connected conexión efectiva: hay telemetría, no está antigua y no se perdió el enlace.
 * @property activeAlerts alertas todavía sin reconocer.
 * @property pendingTypes tipos de orden que están esperando respuesta (PENDING).
 */
data class RuleInput(
    val telemetry: Telemetry?,
    val connected: Boolean,
    val activeAlerts: List<RobotEvent> = emptyList(),
    val pendingTypes: Set<CommandType> = emptySet()
)

/**
 * Reglas de habilitación de comandos (las evalúa el ViewModel, no la UI).
 * Función pura: sin corrutinas ni acceso al simulador.
 */
object CommandRules {
    const val MIN_BATTERY_TO_START = 10
    const val LOW_BATTERY_WARNING = 20

    private val CONFIRMATION_REQUIRED =
        setOf(CommandType.CANCEL_MISSION, CommandType.OPERATIONAL_STOP)

    /** CANCEL_MISSION y OPERATIONAL_STOP piden confirmación antes de enviarse. */
    fun requiresConfirmation(type: CommandType) = type in CONFIRMATION_REQUIRED

    /** Zonas que esperan una revisión manual del operador. */
    fun zonesNeedingReview(telemetry: Telemetry?): List<Zone> =
        if (telemetry == null) emptyList() else Zone.entries.filter {
            telemetry.zonesStatus[it] == ZoneStatus.MANUAL_REVIEW ||
                telemetry.zonesStatus[it] == ZoneStatus.NOT_VERIFIABLE
        }

    /** [zone] solo aplica a MANUAL_REVIEW; si es null alcanza con que alguna zona lo necesite. */
    fun evaluate(type: CommandType, input: RuleInput, zone: Zone? = null): CommandAvailability {
        // La detención operativa nunca se deshabilita (sin conexión queda NO_RESPONSE).
        if (type == CommandType.OPERATIONAL_STOP) return CommandAvailability.ENABLED

        val t = input.telemetry
        if (t == null || !input.connected) {
            return CommandAvailability.disabled("Sin conexión con el robot.")
        }
        if (type in input.pendingTypes) {
            return CommandAvailability.disabled("Esperando respuesta del robot…")
        }

        val criticalAlert = input.activeAlerts.any { it.severity == Severity.CRITICAL }
        val routeBlocked = input.activeAlerts.any { it.type == EventType.OBSTACLE }

        return when (type) {
            CommandType.START_MISSION -> when {
                t.state == RobotState.ON_MISSION || t.state == RobotState.PAUSED ||
                    t.state == RobotState.RETURNING ->
                    CommandAvailability.disabled("Ya hay una ronda en curso.")
                t.state == RobotState.STOPPED ->
                    CommandAvailability.disabled("El robot está detenido: volvé al punto seguro.")
                t.state == RobotState.FAULT ->
                    CommandAvailability.disabled("El robot tiene una falla.")
                t.connection != ConnectionState.CONNECTED ->
                    CommandAvailability.disabled("Conexión degradada: no se puede iniciar una ronda.")
                t.batteryPercent < MIN_BATTERY_TO_START -> CommandAvailability.disabled(
                    "Batería baja (${t.batteryPercent} %): mínimo $MIN_BATTERY_TO_START % para iniciar."
                )
                criticalAlert ->
                    CommandAvailability.disabled("Hay una alerta crítica sin reconocer.")
                else -> CommandAvailability.ENABLED
            }
            CommandType.PAUSE_MISSION -> when (t.state) {
                RobotState.ON_MISSION -> CommandAvailability.ENABLED
                RobotState.PAUSED -> CommandAvailability.disabled("La ronda ya está pausada.")
                else -> CommandAvailability.disabled("No hay una ronda en curso.")
            }
            CommandType.RESUME_MISSION -> when {
                t.state != RobotState.PAUSED -> CommandAvailability.disabled("La ronda no está pausada.")
                criticalAlert ->
                    CommandAvailability.disabled("Reconocé la alerta antes de reanudar.")
                else -> CommandAvailability.ENABLED
            }
            CommandType.CANCEL_MISSION ->
                if (t.state == RobotState.ON_MISSION || t.state == RobotState.PAUSED) {
                    CommandAvailability.ENABLED
                } else {
                    CommandAvailability.disabled("La ronda ya finalizó.")
                }
            CommandType.RETURN_TO_SAFE_POINT -> when {
                t.state == RobotState.IDLE -> CommandAvailability.disabled("El robot ya está en la base.")
                t.state == RobotState.RETURNING -> CommandAvailability.disabled("El robot ya está volviendo.")
                t.state == RobotState.FAULT -> CommandAvailability.disabled("El robot tiene una falla.")
                routeBlocked -> CommandAvailability.disabled("Ruta bloqueada por un obstáculo.")
                else -> CommandAvailability.ENABLED
            }
            CommandType.ACK_ALERT ->
                if (input.activeAlerts.isEmpty()) {
                    CommandAvailability.disabled("No hay alertas activas.")
                } else {
                    CommandAvailability.ENABLED
                }
            CommandType.MANUAL_REVIEW -> evaluateManualReview(t, zone)
            CommandType.OPERATIONAL_STOP -> CommandAvailability.ENABLED
        }
    }

    private fun evaluateManualReview(t: Telemetry, zone: Zone?): CommandAvailability {
        if (zone == null) {
            return if (zonesNeedingReview(t).isEmpty()) {
                CommandAvailability.disabled("Ninguna zona requiere revisión manual.")
            } else {
                CommandAvailability.ENABLED
            }
        }
        return when (t.zonesStatus[zone] ?: ZoneStatus.PENDING) {
            ZoneStatus.MANUAL_REVIEW, ZoneStatus.NOT_VERIFIABLE -> CommandAvailability.ENABLED
            ZoneStatus.FREE, ZoneStatus.VERIFIED_MANUALLY ->
                CommandAvailability.disabled("La zona ya está verificada.")
            ZoneStatus.PENDING -> CommandAvailability.disabled("La zona todavía no fue visitada.")
            ZoneStatus.PERSON_FOUND ->
                CommandAvailability.disabled("Hay una persona en la zona: no corresponde revisión manual.")
        }
    }
}
