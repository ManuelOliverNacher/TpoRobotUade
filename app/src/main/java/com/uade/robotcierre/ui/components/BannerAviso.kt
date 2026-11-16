package com.uade.robotcierre.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotText

/** Banner de aviso: info, advertencia, error (y OK para "Conexión recuperada"). */
@Composable
fun BannerAviso(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = tone.colors()
    val icon = when (tone) {
        Tone.ERROR, Tone.WARNING -> Icons.Default.Warning
        Tone.OK -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .robotCard(borderColor = c.border, background = c.container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = RobotText.Body, color = c.content, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = RobotText.Subtitle, color = c.content)
            }
        }
    }
}
