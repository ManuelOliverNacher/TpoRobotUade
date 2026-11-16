package com.uade.robotcierre.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotText

/** Chip de estado (conexión, orden o zona). Con [spinner] muestra una ruedita (orden Pendiente). */
@Composable
fun ChipEstado(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    spinner: Boolean = false
) {
    val c = tone.colors()
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .background(c.container, shape)
            .border(1.dp, c.border.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (spinner) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = c.content)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = RobotText.Small.copy(fontWeight = FontWeight.SemiBold), color = c.content)
    }
}

@Composable
fun ChipEstado(spec: ChipSpec, modifier: Modifier = Modifier) =
    ChipEstado(spec.label, spec.tone, modifier, spec.spinner)
