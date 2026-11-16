package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.ui.label
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.AlertUi
import com.uade.robotcierre.viewmodel.CommandAvailability

/** Acción que ofrece una alerta abierta (por ejemplo, "Volver a la base" ante batería baja). */
data class AlertAction(
    val text: String,
    val availability: CommandAvailability,
    val onClick: () -> Unit
)

/**
 * Alerta con icono de severidad, tipo, zona, hora y botón Reconocer.
 * Las cerradas se ven atenuadas y sin botón. [onAcknowledge] null = sin botón.
 */
@Composable
fun ItemAlerta(
    alert: AlertUi,
    modifier: Modifier = Modifier,
    onAcknowledge: (() -> Unit)? = null,
    guidance: String? = null,
    actions: List<AlertAction> = emptyList()
) {
    val event = alert.event
    val tone = event.severity.tone()
    val c = tone.colors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .robotCard(borderColor = if (alert.closed) RobotColors.Border else c.border)
            .alpha(if (alert.closed) 0.6f else 1f)
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(c.content))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (event.severity == Severity.INFO) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = event.severity.label(),
                    tint = c.content,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(event.type.label(), style = RobotText.Subtitle, color = RobotColors.Text, modifier = Modifier.weight(1f))
                if (alert.closed) ChipEstado("Cerrada", Tone.NEUTRAL)
            }
            val where = listOfNotNull(event.zone?.label, formatTime(alert.receivedAt)).joinToString(" · ")
            Text(where, style = RobotText.Small, color = RobotColors.TextSecondary)
            Text(event.details, style = RobotText.Body, color = RobotColors.Text)
            if (guidance != null && !alert.closed) {
                Text(guidance, style = RobotText.Body.copy(fontWeight = FontWeight.SemiBold), color = RobotColors.Text)
            }
            if (!alert.closed && actions.isNotEmpty()) {
                // Las acciones que no corresponden quedan deshabilitadas con su motivo.
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    actions.forEach { action ->
                        BotonComando(
                            text = action.text,
                            onClick = action.onClick,
                            modifier = Modifier.weight(1f),
                            enabled = action.availability.enabled,
                            reason = action.availability.reason
                        )
                    }
                }
            }
            if (!alert.closed && onAcknowledge != null) {
                BotonComando(
                    text = "Reconocer",
                    onClick = onAcknowledge,
                    tone = if (tone == Tone.ERROR) Tone.ERROR else Tone.NEUTRAL,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
