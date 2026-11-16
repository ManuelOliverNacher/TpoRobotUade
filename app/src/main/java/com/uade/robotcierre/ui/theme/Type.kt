package com.uade.robotcierre.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Estilos de texto de la especificación (sección 1). Fuente por defecto: Roboto. */
object RobotText {
    val Title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val Subtitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val BigData = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)

    /** Etiqueta: 12/16 en mayúsculas (usar con `.uppercase()` sobre el texto). */
    val Label = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )

    /** Texto chico de 12 px (motivos de botones deshabilitados, chips). */
    val Small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
}

val Typography = Typography(
    headlineSmall = RobotText.Title,
    titleMedium = RobotText.Subtitle,
    titleLarge = RobotText.Title,
    bodyLarge = RobotText.Body,
    bodyMedium = RobotText.Body,
    bodySmall = RobotText.Small,
    displaySmall = RobotText.BigData,
    labelLarge = RobotText.Subtitle,
    labelMedium = RobotText.Label,
    labelSmall = RobotText.Label
)
