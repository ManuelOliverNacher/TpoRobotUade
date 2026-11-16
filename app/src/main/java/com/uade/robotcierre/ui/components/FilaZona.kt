package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/** Fila de una zona del recorrido: número, nombre y chip de estado. [isCurrent] marca la zona actual. */
@Composable
fun FilaZona(
    zone: Zone,
    status: ZoneStatus,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    stale: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .robotCard(
                borderColor = if (isCurrent) RobotColors.Primary else RobotColors.Border,
                borderWidth = if (isCurrent) 2.dp else 1.dp
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).background(if (isCurrent) RobotColors.Primary else RobotColors.DisabledBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                zone.checkpoint.toString(),
                style = RobotText.Subtitle,
                color = if (isCurrent) androidx.compose.ui.graphics.Color.White else RobotColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(zone.label, style = RobotText.Subtitle, color = RobotColors.Text)
            if (isCurrent) Etiqueta("Zona actual", color = RobotColors.Primary)
        }
        if (stale) {
            EtiquetaAntiguo(Modifier.padding(end = 8.dp))
        }
        ChipEstado(status.chip())
    }
}
