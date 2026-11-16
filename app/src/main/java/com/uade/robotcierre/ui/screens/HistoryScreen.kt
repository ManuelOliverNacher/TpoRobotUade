package com.uade.robotcierre.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.ui.components.BarraSuperior
import com.uade.robotcierre.ui.components.ChipEstado
import com.uade.robotcierre.ui.components.ItemEventoHistorial
import com.uade.robotcierre.ui.components.chip
import com.uade.robotcierre.ui.components.robotCard
import com.uade.robotcierre.ui.formatDateTime
import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState

/**
 * 3.5 Historial (Room): rondas guardadas y, al tocar una, sus eventos debajo.
 * Se lee siempre a través del ViewModel.
 */
@Composable
fun HistoryScreen(
    state: UiState,
    onEvent: (RobotUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSimulation: (() -> Unit)? = null
) {
    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            title = "Historial",
            subtitle = when (val n = state.missions.size) {
                0 -> "Sin rondas guardadas"
                1 -> "1 ronda guardada"
                else -> "$n rondas guardadas"
            },
            chip = state.connectionEffective.chip(),
            onOpenSimulation = onOpenSimulation
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.missions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Todavía no hay rondas registradas.",
                            style = RobotText.Body,
                            color = RobotColors.TextSecondary
                        )
                    }
                }
            }
            items(state.missions, key = { it.id }) { mission ->
                val expanded = state.selectedMissionId == mission.id
                MissionCard(
                    mission = mission,
                    expanded = expanded,
                    events = if (expanded) state.selectedMissionEvents else emptyList(),
                    onToggle = {
                        onEvent(RobotUiEvent.SelectMission(if (expanded) null else mission.id))
                    }
                )
            }
            if (state.eventsOutsideMissions.isNotEmpty()) {
                item {
                    Text("Eventos fuera de una ronda", style = RobotText.Subtitle, color = RobotColors.Text)
                }
                items(state.eventsOutsideMissions, key = { "fuera-${it.id}" }) { ItemEventoHistorial(it) }
            }
        }
    }
}

@Composable
private fun MissionCard(
    mission: MissionRecord,
    expanded: Boolean,
    events: List<EventRecord>,
    onToggle: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().robotCard().clickable(onClick = onToggle).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Ronda ${mission.code}", style = RobotText.Subtitle, color = RobotColors.Text)
                val end = mission.endedAt?.let { " · Fin ${formatTime(it)}" }.orEmpty()
                Text(
                    formatDateTime(mission.startedAt) + end,
                    style = RobotText.Small,
                    color = RobotColors.TextSecondary
                )
                Text(
                    "Zonas verificadas: ${mission.zonesVerified} de ${mission.zonesTotal}",
                    style = RobotText.Body,
                    color = RobotColors.Text
                )
            }
            ChipEstado(mission.result.chip())
        }
        if (!expanded) {
            Text("Tocá para ver los eventos", style = RobotText.Small, color = RobotColors.Primary)
        } else {
            if (events.isEmpty()) {
                Text("Sin eventos en esta ronda.", style = RobotText.Body, color = RobotColors.TextSecondary)
            } else {
                events.forEach { ItemEventoHistorial(it) }
            }
        }
    }
}
