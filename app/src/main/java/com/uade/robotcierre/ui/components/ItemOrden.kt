package com.uade.robotcierre.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.ui.label
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.CommandRecord

/** Orden enviada: comando, hora y chip de estado (con el motivo si fue rechazada o sin respuesta). */
@Composable
fun ItemOrden(record: CommandRecord, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().robotCard().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val zone = record.command.zone?.let { " · ${it.label}" } ?: ""
            Text(record.command.type.label() + zone, style = RobotText.Subtitle, color = RobotColors.Text)
            Text(formatTime(record.command.issuedAt), style = RobotText.Small, color = RobotColors.TextSecondary)
            if (record.reason != null && record.status != CommandStatus.CONFIRMED) {
                val tone = record.status.chip().tone
                Text(record.reason, style = RobotText.Small, color = tone.colors().content)
            }
        }
        ChipEstado(record.status.chip())
    }
}
