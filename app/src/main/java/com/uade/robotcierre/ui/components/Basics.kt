package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

val CardShape = RoundedCornerShape(12.dp)

/** Tarjeta de la especificación: superficie blanca, esquinas de 12 y borde de 1 px. */
fun Modifier.robotCard(
    borderColor: Color = RobotColors.Border,
    borderWidth: Dp = 1.dp,
    background: Color = RobotColors.Surface
): Modifier = this
    .background(background, CardShape)
    .border(borderWidth, borderColor, CardShape)

/** Etiqueta de 12/16 en mayúsculas. */
@Composable
fun Etiqueta(text: String, modifier: Modifier = Modifier, color: Color = RobotColors.TextSecondary) {
    Text(text.uppercase(), modifier = modifier, style = RobotText.Label, color = color)
}
