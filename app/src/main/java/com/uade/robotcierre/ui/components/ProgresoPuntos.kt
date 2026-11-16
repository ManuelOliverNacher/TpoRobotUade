package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/** Estado de un punto del recorrido en [ProgresoPuntos]. */
enum class PuntoEstado { COMPLETED, CURRENT, PENDING }

/**
 * Estado de cada uno de los [total] puntos. [checkpoint] es el punto actual (1..total, 0 sin ronda).
 * Sin [onZone] (el robot volviendo a la base) todos los puntos recorridos quedan completos.
 */
fun puntosDeRonda(checkpoint: Int, total: Int, onZone: Boolean): List<PuntoEstado> =
    (1..total).map { n ->
        when {
            checkpoint == 0 -> PuntoEstado.PENDING
            n < checkpoint -> PuntoEstado.COMPLETED
            n == checkpoint -> if (onZone) PuntoEstado.CURRENT else PuntoEstado.COMPLETED
            else -> PuntoEstado.PENDING
        }
    }

/**
 * Progreso de la ronda: [puntos] numerados en fila, unidos por una línea. El actual va resaltado,
 * los completados llenos y los pendientes vacíos. Con [onDark] usa colores claros para fondos azul oscuro.
 */
@Composable
fun ProgresoPuntos(puntos: List<PuntoEstado>, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val line = if (onDark) Color.White.copy(alpha = 0.35f) else RobotColors.Border
    val fill = if (onDark) DotFillOnDark else RobotColors.Primary
    val fillText = if (onDark) RobotColors.Navy else Color.White
    val emptyBorder = if (onDark) Color.White.copy(alpha = 0.6f) else RobotColors.DisabledText
    val emptyText = if (onDark) Color.White else RobotColors.TextSecondary
    val current = if (onDark) Color.White else RobotColors.Navy
    val currentText = if (onDark) RobotColors.Navy else Color.White
    val ring = if (onDark) DotFillOnDark else RobotColors.Primary

    val done = puntos.count { it == PuntoEstado.COMPLETED }
    val description = "Progreso de la ronda: $done de ${puntos.size} puntos completos"
    Box(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        // La línea corre por el centro de los puntos (de centro a centro).
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(line)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            puntos.forEachIndexed { index, estado ->
                val size = if (estado == PuntoEstado.CURRENT) 36.dp else 28.dp
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    val base = Modifier.size(size)
                    val look = when (estado) {
                        PuntoEstado.COMPLETED -> base.background(fill, CircleShape)
                        PuntoEstado.CURRENT -> base.background(current, CircleShape).border(3.dp, ring, CircleShape)
                        // Pendiente: vacío (relleno del fondo para tapar la línea) y con borde.
                        PuntoEstado.PENDING -> base
                            .background(if (onDark) RobotColors.Navy else RobotColors.Surface, CircleShape)
                            .border(2.dp, emptyBorder, CircleShape)
                    }
                    Box(look, contentAlignment = Alignment.Center) {
                        Text(
                            (index + 1).toString(),
                            style = RobotText.Subtitle,
                            color = when (estado) {
                                PuntoEstado.COMPLETED -> fillText
                                PuntoEstado.CURRENT -> currentText
                                PuntoEstado.PENDING -> emptyText
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Celeste de los puntos completados sobre la tarjeta azul oscuro. */
val DotFillOnDark = Color(0xFF7DB8E8)
