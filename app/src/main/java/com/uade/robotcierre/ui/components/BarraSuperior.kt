package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/**
 * Barra superior azul de las pantallas principales: título, chip de conexión y una línea de apoyo.
 * Si [onOpenSimulation] no es null muestra el ícono "Simulación" (solo modo demo).
 */
@Composable
fun BarraSuperior(
    title: String,
    subtitle: String,
    chip: ChipSpec,
    modifier: Modifier = Modifier,
    onOpenSimulation: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(RobotColors.Primary)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = RobotText.Title, color = Color.White, modifier = Modifier.weight(1f))
            if (onOpenSimulation != null) {
                IconButton(onClick = onOpenSimulation) {
                    Icon(Icons.Default.Settings, contentDescription = "Simulación", tint = Color.White)
                }
            }
            ChipEstado(chip)
        }
        Text(subtitle, style = RobotText.Small, color = Color.White.copy(alpha = 0.85f))
    }
}
