package com.uade.robotcierre.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.viewmodel.AlertUi
import com.uade.robotcierre.viewmodel.CommandRecord

/** Galería de componentes (solo para @Preview): sirve para compararlos con el Figma. */
@Preview(name = "Botones", showBackground = true, widthDp = 360, heightDp = 560)
@Composable
fun PreviewBotones() = Gallery {
    BotonComando("Iniciar ronda", {}, icon = Icons.Default.PlayArrow)
    BotonComando("Iniciar ronda", {}, enabled = false, reason = "Batería baja (9 %): mínimo 10 % para iniciar.")
    BotonComando("Volver a la base", {}, icon = Icons.Default.Home, tone = Tone.WARNING)
    BotonComando("Detención operativa", {}, icon = RobotIcons.Stop, tone = Tone.ERROR)
    BotonComando("Cancelar ronda", {}, icon = Icons.Default.Close, enabled = false, reason = "Esperando respuesta…")
}

@Preview(name = "Chips", showBackground = true, widthDp = 360, heightDp = 520)
@Composable
fun PreviewChips() = Gallery {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectionState.entries.forEach { ChipEstado(it.chip()) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommandStatus.entries.take(2).forEach { ChipEstado(it.chip()) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommandStatus.entries.drop(2).forEach { ChipEstado(it.chip()) }
    }
    ZoneStatus.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { ChipEstado(it.chip()) } }
    }
}

@Preview(name = "Tarjetas y batería", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewTarjetas() = Gallery {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TarjetaDato("Zonas verificadas", "3 de 6", Modifier.weight(1f), updatedAt = "22:41:05")
        TarjetaDato("Personas", "1", Modifier.weight(1f), tone = Tone.ERROR)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TarjetaDato("Localización", "Degradada", Modifier.weight(1f), tone = Tone.WARNING)
        TarjetaDato("Zonas verificadas", "3 de 6", Modifier.weight(1f), stale = true, updatedAt = "22:41:05")
    }
    BarraBateria(72)
    BarraBateria(20)
    BarraBateria(8)
    BarraBateria(72, stale = true)
}

@Preview(name = "Banners y modal", showBackground = true, widthDp = 360, heightDp = 560)
@Composable
fun PreviewBanners() = Gallery {
    BannerAviso("Datos antiguos (más de 5 s). No representan el estado actual.", Tone.WARNING)
    BannerAviso("Sin conexión con el robot. Última actualización 22:41:05.", Tone.ERROR)
    BannerAviso("Hay una persona detectada en Zona techada.", Tone.ERROR, actionLabel = "Ver", onAction = {})
    BannerAviso("La ronda terminó.", Tone.INFO)
    BannerAviso("Conexión recuperada. Reanudá la ronda cuando estés listo.", Tone.OK)
}

@Preview(name = "Alertas, órdenes y zonas", showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
fun PreviewListas() = Gallery {
    fun alert(type: EventType, sev: Severity, zone: Zone?, text: String, closed: Boolean = false) =
        AlertUi(RobotEvent("E-1", type, sev, zone, text), receivedAt = 0L, closed = closed)
    ItemAlerta(
        alert(EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA,
            "Persona detectada en Zona techada. El robot se detuvo a distancia."),
        onAcknowledge = {}
    )
    ItemAlerta(alert(EventType.LOW_BATTERY, Severity.WARNING, null, "Batería al 20 %."), onAcknowledge = {})
    ItemAlerta(alert(EventType.MISSION_COMPLETE, Severity.INFO, null, "Ronda finalizada.", closed = true))
    fun order(status: CommandStatus, reason: String? = null) = CommandRecord(
        Command("CMD-001", CommandType.START_MISSION, 0L, "operador-1"), status, reason
    )
    ItemOrden(order(CommandStatus.PENDING))
    ItemOrden(order(CommandStatus.CONFIRMED))
    ItemOrden(order(CommandStatus.REJECTED, "Batería baja"))
    ItemOrden(order(CommandStatus.NO_RESPONSE, "Sin respuesta del robot en 10 s."))
    FilaZona(Zone.HALL, ZoneStatus.FREE)
    FilaZona(Zone.CORRIDOR, ZoneStatus.FREE)
    FilaZona(Zone.RESTROOM_DOOR, ZoneStatus.MANUAL_REVIEW, isCurrent = true)
    FilaZona(Zone.PERGOLA, ZoneStatus.PERSON_FOUND)
    FilaZona(Zone.LAWN, ZoneStatus.PENDING, stale = true)
}

@Preview(name = "Modal", showBackground = true, widthDp = 360, heightDp = 360)
@Composable
fun PreviewModal() = RobotCierreUadeTheme {
    ModalConfirmacion(
        title = "¿Detención operativa?",
        text = "Es un comando de software simulado y no reemplaza una parada de emergencia física.",
        onConfirm = {},
        onDismiss = {}
    )
}

@Composable
private fun Gallery(content: @Composable () -> Unit) {
    RobotCierreUadeTheme {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}
