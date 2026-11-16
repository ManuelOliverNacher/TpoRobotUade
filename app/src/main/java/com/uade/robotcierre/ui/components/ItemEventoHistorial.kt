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
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.ui.label
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/** Evento guardado en el historial: tipo, zona, hora, detalle y si fue reconocido. */
@Composable
fun ItemEventoHistorial(record: EventRecord, modifier: Modifier = Modifier) {
    val tone = record.severity.tone()
    Column(
        modifier.fillMaxWidth().robotCard(borderColor = tone.colors().border.copy(alpha = 0.5f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.type.label(),
                style = RobotText.Subtitle,
                color = tone.colors().content,
                modifier = Modifier.weight(1f)
            )
            ChipEstado(
                if (record.acknowledged) "Reconocida" else "Abierta",
                if (record.acknowledged) Tone.NEUTRAL else Tone.WARNING
            )
        }
        val where = listOfNotNull(record.zone?.label, formatTime(record.timestamp)).joinToString(" · ")
        Text(where, style = RobotText.Small, color = RobotColors.TextSecondary)
        Text(record.details, style = RobotText.Body, color = RobotColors.Text)
    }
}
