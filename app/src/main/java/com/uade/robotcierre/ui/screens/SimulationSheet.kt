package com.uade.robotcierre.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.ui.components.BotonComando
import com.uade.robotcierre.ui.components.Tone
import com.uade.robotcierre.ui.label
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.CommandAvailability
import com.uade.robotcierre.viewmodel.SimulationAction
import com.uade.robotcierre.viewmodel.UiState

/**
 * Cuándo tiene efecto cada acción del panel de simulación. Las que el simulador ignoraría
 * (por ejemplo, forzar una persona sin ronda en curso) se muestran deshabilitadas con su motivo.
 */
fun simulationAvailability(action: SimulationAction, state: UiState): CommandAvailability {
    val t = state.telemetry
    val disconnected = state.connectionEffective == ConnectionState.DISCONNECTED
    val degraded = state.connectionEffective == ConnectionState.DEGRADED
    fun needsRound(): CommandAvailability? = when {
        disconnected -> CommandAvailability.disabled("Sin conexión con el robot.")
        t?.state != RobotState.ON_MISSION -> CommandAvailability.disabled("Requiere una ronda en curso.")
        else -> null
    }
    return when (action) {
        SimulationAction.DISCONNECT ->
            if (disconnected) CommandAvailability.disabled("Ya está desconectado.") else CommandAvailability.ENABLED
        // Reconectar también normaliza una conexión degradada.
        SimulationAction.RECONNECT ->
            if (disconnected || degraded) CommandAvailability.ENABLED else CommandAvailability.disabled("Hay conexión.")
        SimulationAction.FAULT ->
            if (t?.state == RobotState.FAULT) CommandAvailability.disabled("Ya hay una falla.")
            else needsRound() ?: CommandAvailability.ENABLED
        SimulationAction.DEGRADED_CONNECTION -> when {
            disconnected -> CommandAvailability.disabled("Sin conexión con el robot.")
            degraded -> CommandAvailability.disabled("La conexión ya está degradada.")
            else -> CommandAvailability.ENABLED
        }
        SimulationAction.OBSTACLE -> needsRound()
            ?: if (state.activeAlerts.any { it.event.type == EventType.OBSTACLE }) {
                CommandAvailability.disabled("Ya hay un obstáculo sin reconocer.")
            } else {
                CommandAvailability.ENABLED
            }
        SimulationAction.REJECT_NEXT_COMMAND, SimulationAction.LOW_BATTERY -> CommandAvailability.ENABLED
        SimulationAction.PERSON_DETECTED -> needsRound() ?: CommandAvailability.ENABLED
        SimulationAction.ZONE_NOT_VERIFIABLE -> needsRound()
            ?: if (t?.zone == Zone.RESTROOM_DOOR) {
                CommandAvailability.disabled("El baño siempre requiere revisión manual.")
            } else {
                CommandAvailability.ENABLED
            }
    }
}

/** 3.8 Panel de simulación (hoja inferior, solo modo demo). Cierra la hoja al elegir una acción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationSheet(
    state: UiState,
    onAction: (SimulationAction) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RobotColors.Surface
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Panel de simulación", style = RobotText.Title, color = RobotColors.Text)
            Text("Solo para la demostración.", style = RobotText.Body, color = RobotColors.TextSecondary)
            SimulationAction.entries.forEach { action ->
                val availability = simulationAvailability(action, state)
                BotonComando(
                    text = action.label(),
                    onClick = {
                        onAction(action)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = availability.enabled,
                    reason = availability.reason,
                    tone = Tone.INFO // azul: se distingue de los controles operativos
                )
            }
        }
    }
}
