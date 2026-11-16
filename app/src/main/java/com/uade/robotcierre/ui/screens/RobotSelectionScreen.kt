package com.uade.robotcierre.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.components.BarraBateria
import com.uade.robotcierre.ui.components.BotonComando
import com.uade.robotcierre.ui.components.ChipEstado
import com.uade.robotcierre.ui.components.Etiqueta
import com.uade.robotcierre.ui.components.Tone
import com.uade.robotcierre.ui.components.chip
import com.uade.robotcierre.ui.components.robotCard
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.UiState

/** Datos fijos del único robot de la demo (no vienen en la telemetría). */
object RobotInfo {
    const val MODEL = "Unitree Go2 EDU"
    const val ID = "go2-edu-01"
    const val SITE = "Campus Urbano · planta baja"
}

/** 3.1 Selección de robot. */
@Composable
fun RobotSelectionScreen(state: UiState, onSupervise: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Elegí un robot", style = RobotText.Title, color = RobotColors.Text)

        Column(
            Modifier.fillMaxWidth().robotCard().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${RobotInfo.MODEL} · ${RobotInfo.ID}", style = RobotText.Subtitle, color = RobotColors.Text)
                    Text(RobotInfo.SITE, style = RobotText.Body, color = RobotColors.TextSecondary)
                }
                ChipEstado("SIMULADO", Tone.INFO)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Etiqueta("Conexión")
                ChipEstado(state.connectionEffective.chip())
            }
            Column(Modifier.alpha(if (state.isStale) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Etiqueta("Batería")
                val battery = state.telemetry?.batteryPercent
                if (battery != null) {
                    BarraBateria(battery, stale = state.isStale)
                } else {
                    Text("Sin datos", style = RobotText.Body, color = RobotColors.TextSecondary)
                }
            }
        }

        BotonComando(
            text = "Supervisar",
            icon = Icons.Default.PlayArrow,
            onClick = onSupervise,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "El robot es simulado. No hay hardware conectado.",
            style = RobotText.Small,
            color = RobotColors.TextSecondary
        )
    }
}
