package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.ui.label
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/** Ícono del estado operativo (todos de `material-icons-core` o dibujados a mano). */
fun RobotState.icon(): ImageVector = when (this) {
    RobotState.IDLE -> Icons.Default.Home
    RobotState.ON_MISSION -> Icons.Default.PlayArrow
    RobotState.PAUSED -> RobotIcons.Pause
    RobotState.RETURNING -> Icons.AutoMirrored.Filled.ArrowBack
    RobotState.STOPPED -> RobotIcons.Stop
    RobotState.FAULT -> Icons.Default.Warning
}

/** Tono del chip de estado: verde en ronda, ámbar en pausa, rojo detenido/falla y celeste el resto. */
fun RobotState.tone(): Tone = when (this) {
    RobotState.ON_MISSION -> Tone.OK
    RobotState.PAUSED -> Tone.WARNING
    RobotState.STOPPED, RobotState.FAULT -> Tone.ERROR
    RobotState.IDLE, RobotState.RETURNING -> Tone.INFO
}

/**
 * Tarjeta de estado destacada: fondo azul oscuro con texto blanco, el estado operativo en grande,
 * un chip con ícono y el progreso de la ronda ([puntos] numerados + [detalle], p. ej. la zona actual).
 * Con [stale] el contenido se atenúa y lleva la etiqueta ANTIGUO.
 */
@Composable
fun TarjetaEstado(
    label: String,
    title: String,
    state: RobotState,
    puntos: List<PuntoEstado>,
    detalle: String,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    lines: List<String> = emptyList(),
    updatedAt: String? = null
) {
    Column(
        modifier.fillMaxWidth().background(RobotColors.Navy, CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.alpha(if (stale) 0.5f else 1f), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label.uppercase(), style = RobotText.Label, color = Color.White.copy(alpha = 0.7f))
                Text(
                    title,
                    style = RobotText.BigData.copy(fontSize = 32.sp, lineHeight = 38.sp),
                    color = Color.White
                )
            }
            ChipIconoEstado(state)
        }
        if (stale) EtiquetaAntiguo()
        Column(Modifier.alpha(if (stale) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lines.forEach { Text(it, style = RobotText.Body, color = Color.White.copy(alpha = 0.85f)) }
            ProgresoPuntos(puntos, onDark = true)
            Text(detalle, style = RobotText.Subtitle, color = Color.White)
            if (updatedAt != null) {
                Text("Actualizado $updatedAt", style = RobotText.Small, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/** Chip redondo con el ícono del estado (el nombre del estado va en el título de la tarjeta). */
@Composable
private fun ChipIconoEstado(state: RobotState) {
    val c = state.tone().colors()
    Box(
        Modifier
            .size(44.dp)
            .background(c.container, CircleShape)
            .semantics { contentDescription = "Estado: ${state.label()}" },
        contentAlignment = Alignment.Center
    ) {
        Icon(state.icon(), contentDescription = null, tint = c.content, modifier = Modifier.size(26.dp))
    }
}
