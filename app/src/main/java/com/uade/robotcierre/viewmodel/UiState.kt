package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone

/** Nivel de batería para el estado visual (normal / advertencia / error). */
enum class BatteryLevel { OK, WARNING, CRITICAL }

/** Alerta recibida del robot. [closed] = ya reconocida o resuelta. */
data class AlertUi(val event: RobotEvent, val receivedAt: Long, val closed: Boolean)

/** Una orden enviada y su estado. Enviar NO equivale a ejecutar: mirar [status]. */
data class CommandRecord(
    val command: Command,
    val status: CommandStatus,
    val reason: String? = null,
    val resolvedAt: Long? = null
)

/** Todo lo que la UI necesita para dibujarse. La UI no calcula nada por su cuenta. */
data class UiState(
    val telemetry: Telemetry? = null,
    /** Hora (epoch ms) del último paquete de telemetría recibido; se conserva si los datos son antiguos. */
    val lastUpdateMillis: Long? = null,
    /** true si pasaron más de 5 s sin telemetría nueva: los datos NO son el estado actual. */
    val isStale: Boolean = false,
    val connectionEffective: ConnectionState = ConnectionState.DISCONNECTED,
    val alerts: List<AlertUi> = emptyList(),
    val commands: List<CommandRecord> = emptyList(),
    val availability: Map<CommandType, CommandAvailability> = emptyMap(),
    val zonesNeedingReview: List<Zone> = emptyList(),
    /** Comando que espera confirmación del operador (cancelar o detener), o null. */
    val confirmation: CommandType? = null,
    val simulationAvailable: Boolean = false,
    val batteryLevel: BatteryLevel = BatteryLevel.OK,
    /** Historial (Room): rondas, de la más nueva a la más vieja. Vacío si no hay historial. */
    val missions: List<MissionRecord> = emptyList(),
    /** Eventos que ocurrieron fuera de una ronda. */
    val eventsOutsideMissions: List<EventRecord> = emptyList(),
    val selectedMissionId: Long? = null,
    /** Eventos de la ronda elegida con [RobotUiEvent.SelectMission]. */
    val selectedMissionEvents: List<EventRecord> = emptyList()
) {
    val activeAlerts: List<AlertUi> get() = alerts.filter { !it.closed }
}
