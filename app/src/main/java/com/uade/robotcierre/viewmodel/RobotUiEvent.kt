package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.Zone

/** Acciones del panel de simulación (solo modo demo). */
enum class SimulationAction {
    DISCONNECT,
    RECONNECT,
    REJECT_NEXT_COMMAND,
    LOW_BATTERY,
    PERSON_DETECTED,
    ZONE_NOT_VERIFIABLE
}

/** Eventos que la UI envía al ViewModel. */
sealed interface RobotUiEvent {
    /** [zone] solo se usa con MANUAL_REVIEW. */
    data class RequestCommand(val type: CommandType, val zone: Zone? = null) : RobotUiEvent
    data object ConfirmPending : RobotUiEvent
    data object DismissConfirmation : RobotUiEvent
    data class Simulate(val action: SimulationAction) : RobotUiEvent

    /** Elige una ronda del historial para ver sus eventos; null limpia la selección. */
    data class SelectMission(val missionId: Long?) : RobotUiEvent
}
