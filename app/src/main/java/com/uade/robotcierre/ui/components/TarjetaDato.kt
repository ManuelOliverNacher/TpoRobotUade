package com.uade.robotcierre.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/**
 * Tarjeta con etiqueta + valor grande + hora de actualización. Con [stale] el contenido se ve al 50 %
 * y lleva la etiqueta ANTIGUO: los datos no representan el estado actual.
 */
@Composable
fun TarjetaDato(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.NEUTRAL,
    updatedAt: String? = null,
    stale: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    val valueColor = when (tone) {
        Tone.WARNING -> RobotColors.Warning
        Tone.ERROR -> RobotColors.Error
        Tone.OK -> RobotColors.Ok
        Tone.INFO -> RobotColors.Info
        Tone.DISABLED -> RobotColors.DisabledText
        Tone.NEUTRAL -> RobotColors.Text
    }
    val border = when (tone) {
        Tone.WARNING, Tone.ERROR -> tone.colors().border
        else -> RobotColors.Border
    }
    Column(modifier.robotCard(borderColor = border).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Etiqueta(label, Modifier.weight(1f).alpha(if (stale) 0.5f else 1f))
            if (stale) EtiquetaAntiguo()
        }
        Column(Modifier.alpha(if (stale) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(4.dp))
            Text(value, style = RobotText.BigData, color = valueColor)
            extra?.invoke(this)
            if (updatedAt != null) {
                Text("Actualizado $updatedAt", style = RobotText.Small, color = RobotColors.TextSecondary)
            }
        }
    }
}

/** Etiqueta "ANTIGUO" para datos de más de 5 s. */
@Composable
fun EtiquetaAntiguo(modifier: Modifier = Modifier) {
    ChipEstado("ANTIGUO", Tone.WARNING, modifier)
}
