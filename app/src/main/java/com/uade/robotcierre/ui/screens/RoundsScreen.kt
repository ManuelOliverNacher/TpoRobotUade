package com.uade.robotcierre.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.components.BannerAviso
import com.uade.robotcierre.ui.components.BarraSuperior
import com.uade.robotcierre.ui.components.BotonComando
import com.uade.robotcierre.ui.components.Etiqueta
import com.uade.robotcierre.ui.components.EtiquetaAntiguo
import com.uade.robotcierre.ui.components.FilaZona
import com.uade.robotcierre.ui.components.TarjetaEstado
import com.uade.robotcierre.ui.components.puntosDeRonda
import com.uade.robotcierre.ui.components.Tone
import com.uade.robotcierre.ui.components.chip
import com.uade.robotcierre.ui.components.colors
import com.uade.robotcierre.ui.components.robotCard
import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.CommandAvailability
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState

/** 3.3 Rondas: ronda actual, resumen de cierre y las 6 zonas del recorrido en orden. */
@Composable
fun RoundsScreen(
    state: UiState,
    onEvent: (RobotUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSimulation: (() -> Unit)? = null
) {
    val t = state.telemetry
    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            title = "Rondas",
            subtitle = "${RobotInfo.ID} · ${updatedText(state)}",
            chip = state.connectionEffective.chip(),
            onOpenSimulation = onOpenSimulation
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isStale) {
                BannerAviso("Datos antiguos (más de 5 s). No representan el estado actual.", Tone.WARNING)
            }
            CurrentRoundCard(state)

            roundSummary(t, state.missions.firstOrNull()?.result)?.let { summary ->
                val availability = state.availability[CommandType.MANUAL_REVIEW]
                    ?: CommandAvailability.disabled("Sin datos del robot.")
                Column(
                    Modifier.fillMaxWidth().robotCard(borderColor = summary.tone.colors().border).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Etiqueta("Resumen de cierre")
                    Text(summary.title, style = RobotText.Subtitle, color = RobotColors.Text)
                    summary.lines.forEach { Text(it, style = RobotText.Body, color = RobotColors.Text) }
                    summary.reviewZones.firstOrNull()?.let { zone ->
                        BotonComando(
                            text = "Registrar revisión manual",
                            onClick = { onEvent(RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, zone)) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.Check,
                            enabled = availability.enabled,
                            reason = availability.reason,
                            hint = "Zona: ${zone.label}"
                        )
                    }
                }
            }

            Text("Recorrido", style = RobotText.Subtitle, color = RobotColors.Text)
            Zone.entries.forEach { zone ->
                FilaZona(
                    zone = zone,
                    status = t?.zonesStatus?.get(zone) ?: ZoneStatus.PENDING,
                    isCurrent = t?.zone == zone,
                    stale = state.isStale
                )
            }
        }
    }
}

@Composable
private fun CurrentRoundCard(state: UiState) {
    val t = state.telemetry
    if (t?.missionId != null) {
        val started = state.missions
            .firstOrNull { it.code == t.missionId && it.result == MissionResult.IN_PROGRESS }
            ?.startedAt
        // Misma tarjeta azul oscuro del Panel, con los 6 puntos numerados.
        TarjetaEstado(
            label = "Ronda actual",
            title = "Ronda ${t.missionId}",
            state = t.state,
            puntos = puntosDeRonda(t.checkpoint, t.total, onZone = t.zone != null),
            detalle = progressText(t),
            stale = state.isStale,
            lines = listOf(started?.let { "Inicio ${formatTime(it)}" } ?: "Inicio —")
        )
    } else {
        Column(
            Modifier.fillMaxWidth().robotCard().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row {
                Etiqueta("Ronda actual", Modifier.weight(1f))
                if (state.isStale) EtiquetaAntiguo()
            }
            Column(Modifier.alpha(if (state.isStale) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sin ronda en curso", style = RobotText.Subtitle, color = RobotColors.Text)
                Text("Iniciá una ronda desde el Panel.", style = RobotText.Body, color = RobotColors.TextSecondary)
            }
        }
    }
}
