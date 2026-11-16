package com.uade.robotcierre.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/**
 * Botón de comando de 52 px de alto. Si [enabled] es false se ve deshabilitado y muestra
 * debajo el [reason] en 12 px; habilitado puede mostrar un [hint]. [tone] elige normal (primario), advertencia o error (rojo).
 */
@Composable
fun BotonComando(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    reason: String? = null,
    tone: Tone = Tone.NEUTRAL,
    hint: String? = null
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = tone.solid(),
                contentColor = Color.White,
                disabledContainerColor = RobotColors.DisabledBg,
                disabledContentColor = RobotColors.DisabledText
            )
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = RobotText.Subtitle, textAlign = TextAlign.Center, maxLines = 2)
        }
        // Deshabilitado: el motivo. Habilitado: una pista opcional (por ejemplo, la zona a revisar).
        val caption = if (!enabled) reason else hint
        if (caption != null) {
            Text(
                caption,
                style = RobotText.Small,
                color = RobotColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
