package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.BatteryLevel

/** Color de la batería: verde > 20 %, ámbar ≤ 20 %, rojo < 10 %. */
fun BatteryLevel.tone() = when (this) {
    BatteryLevel.OK -> Tone.OK
    BatteryLevel.WARNING -> Tone.WARNING
    BatteryLevel.CRITICAL -> Tone.ERROR
}

/** Barra de batería con porcentaje. [stale] la muestra al 50 %. */
@Composable
fun BarraBateria(
    percent: Int,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    showLabel: Boolean = true
) {
    val value = percent.coerceIn(0, 100)
    val color = BatteryLevel.of(value).tone().colors().content
    Column(modifier.alpha(if (stale) 0.5f else 1f)) {
        if (showLabel) {
            Text("$value %", style = RobotText.BigData, color = color)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(RobotColors.DisabledBg)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value / 100f)
                    .background(color)
            )
        }
    }
}
