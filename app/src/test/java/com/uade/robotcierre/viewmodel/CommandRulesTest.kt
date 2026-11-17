package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRulesTest {

    private fun input(
        state: RobotState = RobotState.IDLE,
        battery: Int = 100,
        connection: ConnectionState = ConnectionState.CONNECTED,
        connected: Boolean = true,
        alerts: List<com.uade.robotcierre.model.RobotEvent> = emptyList(),
        pending: Set<CommandType> = emptySet(),
        statuses: Map<Zone, ZoneStatus> = Zone.entries.associateWith { ZoneStatus.PENDING }
    ) = RuleInput(
        telemetry = testTelemetry(state = state, battery = battery, connection = connection, statuses = statuses),
        connected = connected,
        activeAlerts = alerts,
        pendingTypes = pending
    )

    private fun enabled(type: CommandType, input: RuleInput, zone: Zone? = null) =
        CommandRules.evaluate(type, input, zone).enabled

    private fun assertDisabled(
        type: CommandType,
        input: RuleInput,
        reasonPart: String,
        zone: Zone? = null
    ) {
        val a = CommandRules.evaluate(type, input, zone)
        assertFalse("$type debería estar deshabilitado", a.enabled)
        assertNotNull(a.reason)
        assertTrue("motivo='${a.reason}' no contiene '$reasonPart'", a.reason!!.contains(reasonPart))
    }

    private val critical = testEvent(severity = Severity.CRITICAL)
    private val warning = testEvent(type = EventType.LOW_BATTERY, severity = Severity.WARNING)

    @Test
    fun laDetencionOperativaSiempreEstaHabilitada() {
        val sinDatos = RuleInput(telemetry = null, connected = false)
        assertTrue(enabled(CommandType.OPERATIONAL_STOP, sinDatos))
        val conPendiente = input(connected = false, pending = setOf(CommandType.OPERATIONAL_STOP))
        assertTrue(enabled(CommandType.OPERATIONAL_STOP, conPendiente))
        RobotState.entries.forEach {
            assertTrue(enabled(CommandType.OPERATIONAL_STOP, input(state = it)))
        }
    }

    @Test
    fun sinConexionOSinDatosTodoSeDeshabilitaMenosLaDetencion() {
        val sinDatos = RuleInput(telemetry = null, connected = false)
        val antiguos = input(state = RobotState.ON_MISSION, connected = false)
        (CommandType.entries - CommandType.OPERATIONAL_STOP).forEach {
            assertDisabled(it, sinDatos, "Sin conexión")
            assertDisabled(it, antiguos, "Sin conexión")
        }
    }

    @Test
    fun iniciarRondaSoloEnIdleConBateriaConexionYSinAlertasCriticas() {
        assertTrue(enabled(CommandType.START_MISSION, input()))
        assertDisabled(CommandType.START_MISSION, input(state = RobotState.ON_MISSION), "ronda en curso")
        assertDisabled(CommandType.START_MISSION, input(state = RobotState.PAUSED), "ronda en curso")
        assertDisabled(CommandType.START_MISSION, input(state = RobotState.RETURNING), "ronda en curso")
        assertDisabled(CommandType.START_MISSION, input(state = RobotState.STOPPED), "detenido")
        assertDisabled(CommandType.START_MISSION, input(state = RobotState.FAULT), "falla")
        assertDisabled(CommandType.START_MISSION, input(connection = ConnectionState.DEGRADED), "degradada")
        assertDisabled(CommandType.START_MISSION, input(alerts = listOf(critical)), "alerta crítica")
        assertTrue(enabled(CommandType.START_MISSION, input(alerts = listOf(warning))))
        assertDisabled(
            CommandType.START_MISSION,
            input(pending = setOf(CommandType.START_MISSION)),
            "Esperando respuesta"
        )
    }

    @Test
    fun limitesDeBateriaParaIniciar() {
        assertDisabled(CommandType.START_MISSION, input(battery = 9), "Batería baja")
        assertTrue(enabled(CommandType.START_MISSION, input(battery = 10)))
        assertTrue(enabled(CommandType.START_MISSION, input(battery = 20)))
    }

    @Test
    fun pausarSoloConRondaEnCurso() {
        assertTrue(enabled(CommandType.PAUSE_MISSION, input(state = RobotState.ON_MISSION)))
        assertDisabled(CommandType.PAUSE_MISSION, input(state = RobotState.IDLE), "No hay una ronda")
        assertDisabled(CommandType.PAUSE_MISSION, input(state = RobotState.PAUSED), "ya está pausada")
        assertDisabled(CommandType.PAUSE_MISSION, input(state = RobotState.RETURNING), "No hay una ronda")
    }

    @Test
    fun reanudarSoloPausadaConConexionYSinAlertaCriticaPendiente() {
        assertTrue(enabled(CommandType.RESUME_MISSION, input(state = RobotState.PAUSED)))
        assertTrue(
            enabled(
                CommandType.RESUME_MISSION,
                input(state = RobotState.PAUSED, connection = ConnectionState.DEGRADED)
            )
        )
        assertDisabled(
            CommandType.RESUME_MISSION,
            input(state = RobotState.PAUSED, alerts = listOf(critical)),
            "Reconocé la alerta"
        )
        assertTrue(
            enabled(CommandType.RESUME_MISSION, input(state = RobotState.PAUSED, alerts = listOf(warning)))
        )
        assertDisabled(CommandType.RESUME_MISSION, input(state = RobotState.ON_MISSION), "no está pausada")
        assertDisabled(
            CommandType.RESUME_MISSION,
            input(state = RobotState.PAUSED, connected = false),
            "Sin conexión"
        )
    }

    @Test
    fun cancelarSoloEnCursoOPausada() {
        assertTrue(enabled(CommandType.CANCEL_MISSION, input(state = RobotState.ON_MISSION)))
        assertTrue(enabled(CommandType.CANCEL_MISSION, input(state = RobotState.PAUSED)))
        listOf(RobotState.IDLE, RobotState.STOPPED).forEach {
            assertDisabled(CommandType.CANCEL_MISSION, input(state = it), "ya finalizó")
        }
        // Mientras vuelve a la base no es que la ronda "finalizó": el robot ya está regresando.
        assertEquals(
            "El robot ya está volviendo a la base.",
            CommandRules.evaluate(CommandType.CANCEL_MISSION, input(state = RobotState.RETURNING)).reason
        )
    }

    @Test
    fun volverAlPuntoSeguroConRutaLibre() {
        listOf(RobotState.ON_MISSION, RobotState.PAUSED, RobotState.STOPPED).forEach {
            assertTrue(enabled(CommandType.RETURN_TO_SAFE_POINT, input(state = it)))
        }
        assertDisabled(CommandType.RETURN_TO_SAFE_POINT, input(state = RobotState.IDLE), "ya está en la base")
        assertDisabled(CommandType.RETURN_TO_SAFE_POINT, input(state = RobotState.RETURNING), "ya está volviendo")
        val obstacle = testEvent(type = EventType.OBSTACLE, severity = Severity.WARNING)
        assertDisabled(
            CommandType.RETURN_TO_SAFE_POINT,
            input(state = RobotState.ON_MISSION, alerts = listOf(obstacle)),
            "Ruta bloqueada"
        )
    }

    @Test
    fun reconocerAlertaSoloSiHayAlertasActivas() {
        assertDisabled(CommandType.ACK_ALERT, input(), "No hay alertas activas")
        assertTrue(enabled(CommandType.ACK_ALERT, input(alerts = listOf(warning))))
    }

    @Test
    fun revisionManualSoloParaZonasQueLaNecesitan() {
        val statuses = Zone.entries.associateWith { ZoneStatus.FREE }.toMutableMap().apply {
            put(Zone.RESTROOM_DOOR, ZoneStatus.MANUAL_REVIEW)
            put(Zone.LAWN, ZoneStatus.NOT_VERIFIABLE)
            put(Zone.PERGOLA, ZoneStatus.PERSON_FOUND)
            put(Zone.ESPLANADE, ZoneStatus.PENDING)
            put(Zone.CORRIDOR, ZoneStatus.VERIFIED_MANUALLY)
        }
        val i = input(statuses = statuses)
        assertTrue(enabled(CommandType.MANUAL_REVIEW, i)) // alguna zona la necesita
        assertTrue(enabled(CommandType.MANUAL_REVIEW, i, Zone.RESTROOM_DOOR))
        assertTrue(enabled(CommandType.MANUAL_REVIEW, i, Zone.LAWN))
        assertDisabled(CommandType.MANUAL_REVIEW, i, "ya está verificada", Zone.HALL)
        assertDisabled(CommandType.MANUAL_REVIEW, i, "ya está verificada", Zone.CORRIDOR)
        assertDisabled(CommandType.MANUAL_REVIEW, i, "todavía no fue visitada", Zone.ESPLANADE)
        assertDisabled(CommandType.MANUAL_REVIEW, i, "persona", Zone.PERGOLA)
        assertEquals(listOf(Zone.RESTROOM_DOOR, Zone.LAWN), CommandRules.zonesNeedingReview(i.telemetry))
        // Sin zonas pendientes de revisión.
        assertDisabled(CommandType.MANUAL_REVIEW, input(), "Ninguna zona")
    }

    @Test
    fun soloCancelarYDetenerPidenConfirmacion() {
        val confirm = CommandType.entries.filter { CommandRules.requiresConfirmation(it) }
        assertEquals(
            setOf(CommandType.CANCEL_MISSION, CommandType.OPERATIONAL_STOP),
            confirm.toSet()
        )
    }

    @Test
    fun conObstaculoCancelarSeDeshabilitaConElMismoMotivoQueVolver() {
        val obstacle = testEvent(type = EventType.OBSTACLE, severity = Severity.WARNING)
        listOf(RobotState.ON_MISSION, RobotState.PAUSED).forEach {
            assertDisabled(CommandType.CANCEL_MISSION, input(state = it, alerts = listOf(obstacle)), "Ruta bloqueada por un obstáculo.")
        }
        // Sin obstáculo sigue habilitado.
        assertTrue(enabled(CommandType.CANCEL_MISSION, input(state = RobotState.ON_MISSION)))
    }

    @Test
    fun conFallaCancelarDiceQueHayUnaFalla() {
        assertDisabled(CommandType.CANCEL_MISSION, input(state = RobotState.FAULT), "El robot tiene una falla.")
    }
}
