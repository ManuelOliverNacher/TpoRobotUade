package com.uade.robotcierre.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.ui.components.BannerAviso
import com.uade.robotcierre.ui.components.BarraBateria
import com.uade.robotcierre.ui.components.BarraSuperior
import com.uade.robotcierre.ui.components.BotonComando
import com.uade.robotcierre.ui.components.BotonVariante
import com.uade.robotcierre.ui.components.TarjetaEstado
import com.uade.robotcierre.ui.components.puntosDeRonda
import com.uade.robotcierre.ui.components.ChipEstado
import com.uade.robotcierre.ui.components.ItemOrden
import com.uade.robotcierre.ui.components.RobotIcons
import com.uade.robotcierre.ui.components.TarjetaDato
import com.uade.robotcierre.ui.components.Tone
import com.uade.robotcierre.ui.components.chip
import com.uade.robotcierre.ui.components.tone
import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.ui.label
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.CommandAvailability
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState

/**
 * 3.2 Panel de supervisión (telemetría + control). Solo dibuja el [state] y envía [onEvent];
 * las reglas de habilitación las evalúa el ViewModel.
 */
@Composable
fun SupervisionPanelScreen(
    state: UiState,
    onEvent: (RobotUiEvent) -> Unit,
    onSeeAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSimulation: (() -> Unit)? = null
) {
    Column(modifier.fillMaxSize()) {
        BarraSuperior(
            title = RobotInfo.ID,
            subtitle = updatedText(state),
            chip = state.connectionEffective.chip(),
            onOpenSimulation = onOpenSimulation
        )
        ConnectionBanners(state)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isStale) {
                BannerAviso("Datos antiguos (más de 5 s). No representan el estado actual.", Tone.WARNING)
            }
            val t = state.telemetry
            if (t == null) {
                BannerAviso("Esperando datos del robot…", Tone.INFO)
            } else {
                TelemetrySection(t, state)
            }
            state.activeAlerts.firstOrNull()?.let { alert ->
                val where = alert.event.zone?.let { " en ${it.label}" }.orEmpty()
                BannerAviso(
                    text = alert.event.type.label() + where,
                    tone = alert.event.severity.tone(),
                    actionLabel = "Ver",
                    onAction = onSeeAlerts
                )
            }
            ControlSection(state, onEvent)
            RecentOrders(state)
        }
        // Fija sobre la barra de navegación: no hay que desplazarse para llegar.
        StopBar(state, onEvent)
    }
}

/**
 * Banners fijos (no se desplazan) bajo la barra: rojo mientras no hay conexión y verde al recuperarla.
 * Con la ronda pausada por la pérdida, el verde recuerda que hay que reanudarla.
 */
@Composable
private fun ConnectionBanners(state: UiState) {
    val t = state.telemetry
    val lost = state.connectionEffective == ConnectionState.DISCONNECTED && t != null
    if (!lost && !state.connectionRecovered) return
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (lost) {
            val last = state.lastUpdateMillis?.let { " Última actualización ${formatTime(it)}." }.orEmpty()
            BannerAviso("Sin conexión con el robot.$last", Tone.ERROR)
        }
        if (state.connectionRecovered) {
            val paused = t?.state == RobotState.PAUSED
            BannerAviso(
                if (paused) "Conexión recuperada. Reanudá la ronda cuando estés listo." else "Conexión recuperada.",
                Tone.OK
            )
        }
    }
}

@Composable
private fun TelemetrySection(t: Telemetry, state: UiState) {
    val stale = state.isStale
    val updated = state.lastUpdateMillis?.let { formatTime(it) }

    // Estado operativo y progreso de la ronda (tarjeta azul destacada).
    TarjetaEstado(
        label = "Estado",
        title = t.state.label(),
        state = t.state,
        puntos = puntosDeRonda(t.checkpoint, t.total, onZone = t.zone != null),
        detalle = progressText(t),
        stale = stale,
        updatedAt = updated
    )

    // Batería y movimiento.
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TarjetaDato(
            label = "Batería",
            value = "${t.batteryPercent} %",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            tone = when (state.batteryLevel) {
                com.uade.robotcierre.viewmodel.BatteryLevel.OK -> Tone.NEUTRAL
                else -> state.batteryLevel.tone()
            },
            stale = stale,
            icon = RobotIcons.Battery
        ) {
            BarraBateria(t.batteryPercent, showLabel = false)
            Text(estimatedText(t), style = RobotText.Small, color = RobotColors.TextSecondary)
        }
        TarjetaDato(
            label = "Movimiento",
            value = movementText(t),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            stale = stale,
            valueStyle = RobotText.Title,
            fitValue = true,
            icon = Icons.AutoMirrored.Filled.ArrowForward
        ) {
            Text(
                if (t.moving) speedText(t) else "0,0 m/s",
                style = RobotText.Small,
                color = RobotColors.TextSecondary
            )
        }
    }

    // Datos propios del servicio. Si el ancho no alcanza para tres tarjetas (pantallas de 360 dp o
    // letra agrandada), las etiquetas se partirían a mitad de palabra: se acomodan 2 + 1.
    val people: @Composable (Modifier) -> Unit = { mod ->
        TarjetaDato(
            "Personas detectadas",
            t.peopleDetected.toString(),
            mod,
            tone = if (t.peopleDetected > 0) Tone.ERROR else Tone.NEUTRAL,
            stale = stale,
            valueStyle = RobotText.Title,
            fitValue = true,
            icon = Icons.Default.Person
        )
    }
    val verified: @Composable (Modifier) -> Unit = { mod ->
        TarjetaDato(
            "Zonas verificadas",
            verifiedText(t),
            mod,
            stale = stale,
            valueStyle = RobotText.Title,
            fitValue = true,
            icon = Icons.Default.CheckCircle
        )
    }
    val localization: @Composable (Modifier) -> Unit = { mod ->
        TarjetaDato(
            "Localización",
            t.localizationQuality.label(),
            mod,
            tone = if (t.localizationQuality == LocalizationQuality.DEGRADED) Tone.WARNING else Tone.NEUTRAL,
            stale = stale,
            valueStyle = RobotText.Title,
            fitValue = true,
            icon = Icons.Default.LocationOn
        )
    }
    BoxWithConstraints {
        val narrow = maxWidth < MIN_WIDTH_FOR_THREE_CARDS || LocalDensity.current.fontScale > 1.1f
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    people(Modifier.weight(1f).fillMaxHeight())
                    verified(Modifier.weight(1f).fillMaxHeight())
                }
                localization(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                people(Modifier.weight(1f).fillMaxHeight())
                verified(Modifier.weight(1f).fillMaxHeight())
                localization(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Ancho útil mínimo para dibujar las tres tarjetas propias en una fila sin partir las etiquetas. */
private val MIN_WIDTH_FOR_THREE_CARDS = 440.dp

@Composable
private fun ControlSection(state: UiState, onEvent: (RobotUiEvent) -> Unit) {
    fun availability(type: CommandType) =
        state.availability[type] ?: CommandAvailability.disabled("Sin datos del robot.")

    fun send(type: CommandType) = onEvent(RobotUiEvent.RequestCommand(type))

    val robotState = state.telemetry?.state
    // Botón principal según el estado: con ronda (en curso, pausada o volviendo) se pausa/reanuda;
    // en cualquier otro estado se inicia.
    val inRound = robotState == RobotState.ON_MISSION || robotState == RobotState.PAUSED ||
        robotState == RobotState.RETURNING
    val primaryType = when {
        robotState == RobotState.PAUSED -> CommandType.RESUME_MISSION
        inRound -> CommandType.PAUSE_MISSION
        else -> CommandType.START_MISSION
    }
    val reviewZone = state.zonesNeedingReview.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Control", style = RobotText.Subtitle, color = RobotColors.Text)
        // Principal: azul lleno.
        ControlButton(
            primaryType, availability(primaryType),
            when (primaryType) {
                CommandType.PAUSE_MISSION -> RobotIcons.Pause
                else -> Icons.Default.PlayArrow
            },
            Modifier.fillMaxWidth()
        ) { send(primaryType) }
        // Secundarios: con borde (azul; rojo para cancelar).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            ControlButton(
                CommandType.RETURN_TO_SAFE_POINT, availability(CommandType.RETURN_TO_SAFE_POINT),
                Icons.Default.Home, Modifier.weight(1f), variant = BotonVariante.OUTLINE
            ) { send(CommandType.RETURN_TO_SAFE_POINT) }
            ControlButton(
                CommandType.CANCEL_MISSION, availability(CommandType.CANCEL_MISSION),
                Icons.Default.Close, Modifier.weight(1f), variant = BotonVariante.OUTLINE, tone = Tone.ERROR
            ) { send(CommandType.CANCEL_MISSION) }
        }
        ControlButton(
            CommandType.MANUAL_REVIEW, availability(CommandType.MANUAL_REVIEW),
            Icons.Default.Check, Modifier.fillMaxWidth(),
            hint = reviewZone?.let { "Zona: ${it.label}" },
            variant = BotonVariante.OUTLINE
        ) { reviewZone?.let { onEvent(RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, it)) } }
    }
}

/**
 * Detención operativa y su aviso, fijos abajo (sobre la barra de navegación). Nunca se deshabilita.
 */
@Composable
private fun StopBar(state: UiState, onEvent: (RobotUiEvent) -> Unit) {
    val disconnected = state.connectionEffective == ConnectionState.DISCONNECTED
    Column(Modifier.fillMaxWidth().background(RobotColors.Surface)) {
        HorizontalDivider(color = RobotColors.Border)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BotonComando(
                text = CommandType.OPERATIONAL_STOP.label(),
                onClick = { onEvent(RobotUiEvent.RequestCommand(CommandType.OPERATIONAL_STOP)) },
                modifier = Modifier.fillMaxWidth(),
                icon = RobotIcons.Stop,
                tone = Tone.ERROR,
                hint = if (disconnected) "Puede quedar sin respuesta." else null
            )
            Text(
                "Es un comando de software simulado. No reemplaza una parada de emergencia física.",
                style = RobotText.Small,
                color = RobotColors.Error
            )
        }
    }
}

@Composable
private fun ControlButton(
    type: CommandType,
    availability: CommandAvailability,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    hint: String? = null,
    variant: BotonVariante = BotonVariante.FILLED,
    tone: Tone = Tone.NEUTRAL,
    onClick: () -> Unit
) {
    BotonComando(
        text = type.label(),
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = availability.enabled,
        reason = availability.reason,
        hint = hint,
        tone = tone,
        variant = variant
    )
}

@Composable
private fun RecentOrders(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Órdenes recientes", style = RobotText.Subtitle, color = RobotColors.Text)
        if (state.commands.isEmpty()) {
            Text("Todavía no enviaste órdenes.", style = RobotText.Body, color = RobotColors.TextSecondary)
        } else {
            state.commands.take(5).forEach { ItemOrden(it) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
