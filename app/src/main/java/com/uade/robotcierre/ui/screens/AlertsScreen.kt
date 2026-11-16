package com.uade.robotcierre.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.ui.components.BarraSuperior
import com.uade.robotcierre.ui.components.BotonComando
import com.uade.robotcierre.ui.components.ItemAlerta
import com.uade.robotcierre.ui.components.chip
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.CommandAvailability
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState

private const val INCIDENT_GUIDANCE = "Elegí: avisar a seguridad, continuar o volver a la base."

/**
 * 3.4 Alertas: las abiertas arriba y las cerradas atenuadas, la más nueva primero.
 * "Reconocer alertas" cierra todas las abiertas (así lo hace el robot: el contrato no indica cuál).
 */
@Composable
fun AlertsScreen(
    state: UiState,
    onEvent: (RobotUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSimulation: (() -> Unit)? = null
) {
    val open = state.activeAlerts.size
    val ack = state.availability[CommandType.ACK_ALERT] ?: CommandAvailability.disabled("Sin datos del robot.")
    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            title = "Alertas",
            subtitle = when (open) {
                0 -> "Sin alertas abiertas"
                1 -> "1 abierta"
                else -> "$open abiertas"
            },
            chip = state.connectionEffective.chip(),
            onOpenSimulation = onOpenSimulation
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BotonComando(
                text = "Reconocer alertas",
                onClick = { onEvent(RobotUiEvent.RequestCommand(CommandType.ACK_ALERT)) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Check,
                enabled = ack.enabled,
                reason = ack.reason
            )
            if (state.alerts.isEmpty()) {
                Text("No hay alertas.", style = RobotText.Body, color = RobotColors.TextSecondary)
            } else {
                // La lista ya viene con la más nueva primero; el orden es estable.
                state.alerts.sortedBy { it.closed }.forEach { alert ->
                    ItemAlerta(
                        alert = alert,
                        guidance = if (alert.event.type == EventType.PERSON_DETECTED) INCIDENT_GUIDANCE else null
                    )
                }
            }
        }
    }
}
