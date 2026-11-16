package com.uade.robotcierre.ui.components

import androidx.compose.ui.graphics.Color
import com.uade.robotcierre.ui.theme.RobotColors

/**
 * Estado visual de un componente. Cubre los 4 estados de la especificación:
 * normal ([NEUTRAL], [OK], [INFO]), deshabilitado ([DISABLED]), advertencia ([WARNING]) y error ([ERROR]).
 */
enum class Tone { NEUTRAL, OK, INFO, WARNING, ERROR, DISABLED }

data class ToneColors(val container: Color, val content: Color, val border: Color)

/** Colores suaves (chips, banners, tarjetas). */
fun Tone.colors(): ToneColors = when (this) {
    Tone.NEUTRAL -> ToneColors(RobotColors.DisabledBg, RobotColors.TextSecondary, RobotColors.Border)
    Tone.OK -> ToneColors(RobotColors.OkBg, RobotColors.Ok, RobotColors.Ok)
    Tone.INFO -> ToneColors(RobotColors.InfoBg, RobotColors.Info, RobotColors.Info)
    Tone.WARNING -> ToneColors(RobotColors.WarningBg, RobotColors.Warning, RobotColors.Warning)
    Tone.ERROR -> ToneColors(RobotColors.ErrorBg, RobotColors.Error, RobotColors.Error)
    Tone.DISABLED -> ToneColors(RobotColors.DisabledBg, RobotColors.DisabledText, RobotColors.Border)
}

/** Color sólido para botones: el neutro es el primario de la app. */
fun Tone.solid(): Color = when (this) {
    Tone.NEUTRAL -> RobotColors.Primary
    Tone.OK -> RobotColors.Ok
    Tone.INFO -> RobotColors.Info
    Tone.WARNING -> RobotColors.Warning
    Tone.ERROR -> RobotColors.Error
    Tone.DISABLED -> RobotColors.DisabledBg
}
