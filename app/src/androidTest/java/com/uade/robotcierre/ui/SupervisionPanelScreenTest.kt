package com.uade.robotcierre.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.screens.SupervisionPanelScreen
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.viewmodel.AlertUi
import com.uade.robotcierre.viewmodel.CommandRecord
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas del Panel con estados armados a mano (sin ViewModel ni simulador). */
@RunWith(AndroidJUnit4::class)
class SupervisionPanelScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<RobotUiEvent>()
    private var seeAlertsCalled = false

    private fun show(state: UiState) {
        rule.setContent {
            RobotCierreUadeTheme {
                SupervisionPanelScreen(state, onEvent = { events += it }, onSeeAlerts = { seeAlertsCalled = true })
            }
        }
    }

    private fun count(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private val disclaimer = "Es un comando de software simulado. No reemplaza una parada de emergencia física."

    @Test
    fun enReposoSoloSePuedeIniciarYSeVeElMotivoDeLosDemas() {
        show(uiStateOf(telemetryOf()))
        rule.onNodeWithText("Reposo").assertIsDisplayed()
        rule.onNodeWithText("Sin ronda en curso").assertIsDisplayed()
        rule.onNodeWithText("Conectado").assertIsDisplayed()

        // En reposo el botón principal es "Iniciar ronda"; Pausar/Reanudar no se ofrece.
        rule.onNodeWithText("Iniciar ronda").performScrollTo().assertIsEnabled()
        assertEquals(0, count("Pausar ronda"))
        assertEquals(0, count("Reanudar ronda"))
        rule.onNodeWithText("Cancelar ronda").assertIsNotEnabled()
        rule.onNodeWithText("Volver a la base").assertIsNotEnabled()
        rule.onNodeWithText("El robot ya está en la base.").assertExists()
        rule.onNodeWithText("Revisión manual").assertIsNotEnabled()
        rule.onNodeWithText("Ninguna zona requiere revisión manual.").assertExists()
    }

    @Test
    fun laDetencionSiempreEstaHabilitadaYLaLeyendaSiempreVisible() {
        show(uiStateOf(telemetryOf()))
        // La detención y su aviso están fijos abajo: se ven sin desplazarse.
        rule.onNodeWithText("Detención operativa").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText(disclaimer).assertIsDisplayed()
    }

    @Test
    fun laTarjetaDeEstadoMuestraElEstadoGrandeConSuIconoYLosSeisPuntos() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.PERGOLA, moving = true)))
        rule.onNodeWithText("En ronda").assertIsDisplayed()
        rule.onNodeWithContentDescription("Estado: En ronda").assertIsDisplayed()
        rule.onNodeWithContentDescription("Progreso de la ronda: 3 de 6 puntos completos").assertIsDisplayed()
        // Los 6 puntos van numerados.
        (1..6).forEach { n -> assertTrue(count(n.toString()) >= 1) }
    }

    @Test
    fun enRondaMuestraProgresoMovimientoYHabilitaPausar() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.PERGOLA, battery = 72, moving = true)))
        rule.onNodeWithText("En ronda").assertIsDisplayed()
        rule.onNodeWithText("Punto 4 de 6 · Zona techada del patio").assertIsDisplayed()
        rule.onNodeWithText("Caminando").assertIsDisplayed()
        rule.onNodeWithText("0,8 m/s").assertIsDisplayed()
        rule.onNodeWithText("72 %").assertIsDisplayed()

        rule.onNodeWithText("Pausar ronda").performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Cancelar ronda").assertIsEnabled()
        // Con ronda en curso el principal es Pausar y ya no se ofrece Iniciar.
        assertEquals(0, count("Iniciar ronda"))
    }

    @Test
    fun enPausaElBotonPasaAReanudar() {
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.HALL)))
        rule.onNodeWithText("Reanudar ronda").performScrollTo().assertIsEnabled()
        assertEquals(0, count("Pausar ronda"))
        rule.onNodeWithText("Pausado").assertIsDisplayed()
    }

    @Test
    fun conDatosAntiguosSeAvisaSeMarcaTodoYSoloQuedaLaDetencion() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.LAWN, moving = true), stale = true))
        rule.onNodeWithText("Datos antiguos (más de 5 s). No representan el estado actual.").assertIsDisplayed()
        rule.onNodeWithText("Desconectado").assertIsDisplayed()
        assertEquals(6, count("ANTIGUO")) // Estado, Batería, Movimiento y las 3 tarjetas propias
        assertTrue(rule.onAllNodesWithText("Actualizado", substring = true).fetchSemanticsNodes().isNotEmpty())

        rule.onNodeWithText("Pausar ronda").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Cancelar ronda").assertIsNotEnabled()
        assertTrue(count("Sin conexión con el robot.") >= 3)
        rule.onNodeWithText("Detención operativa").assertIsEnabled()
        rule.onNodeWithText("Puede quedar sin respuesta.").assertExists()
    }

    @Test
    fun losBotonesEnvianEventosYLosDeshabilitadosNo() {
        val statuses = Zone.entries.associateWith { ZoneStatus.FREE }.toMutableMap()
            .apply { put(Zone.RESTROOM_DOOR, ZoneStatus.MANUAL_REVIEW) }
        show(uiStateOf(telemetryOf(statuses = statuses)))

        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.onNodeWithText("Cancelar ronda").performClick() // deshabilitado: no envía nada
        rule.onNodeWithText("Zona: Puerta del baño").assertExists()
        rule.onNodeWithText("Revisión manual").performScrollTo().performClick()
        rule.onNodeWithText("Detención operativa").performClick()

        assertEquals(
            listOf(
                RobotUiEvent.RequestCommand(CommandType.START_MISSION),
                RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, Zone.RESTROOM_DOOR),
                RobotUiEvent.RequestCommand(CommandType.OPERATIONAL_STOP)
            ),
            events
        )
    }

    @Test
    fun lasOrdenesRecientesMuestranSuEstadoYMotivo() {
        fun record(id: Int, status: CommandStatus, reason: String? = null) = CommandRecord(
            Command("CMD-00$id", CommandType.START_MISSION, 1_700_000_000_000L, "operador-1"), status, reason
        )
        show(
            uiStateOf(
                telemetryOf(),
                commands = listOf(
                    record(3, CommandStatus.PENDING),
                    record(2, CommandStatus.REJECTED, "Batería baja"),
                    record(1, CommandStatus.NO_RESPONSE, "Sin respuesta del robot en 10 s.")
                )
            )
        )
        rule.onNodeWithText("Pendiente").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Rechazada").assertExists()
        rule.onNodeWithText("Batería baja").assertExists()
        rule.onNodeWithText("Sin respuesta").assertExists()
        // Mientras la orden está pendiente, ese comando queda deshabilitado.
        // "Iniciar ronda" también figura en la lista de órdenes: se busca el botón por su acción de click.
        rule.onNode(hasText("Iniciar ronda") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithText("Esperando respuesta…").assertExists()
    }

    @Test
    fun sinOrdenesSeMuestraUnTextoVacio() {
        show(uiStateOf(telemetryOf()))
        rule.onNodeWithText("Todavía no enviaste órdenes.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun laUltimaAlertaAbiertaApareceConElBotonVer() {
        val alert = AlertUi(
            RobotEvent("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, "detalle"),
            receivedAt = 1_700_000_000_000L,
            closed = false
        )
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.PERGOLA, people = 1), alerts = listOf(alert)))
        rule.onNodeWithText("Persona detectada en Zona techada del patio").assertIsDisplayed()
        rule.onNodeWithText("Ver").performClick()
        assertTrue(seeAlertsCalled)
        rule.onNodeWithText("Reanudar ronda").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Reconocé la alerta antes de reanudar.").assertExists()
    }

    @Test
    fun sinConexionSeMuestraElBannerRojoFijoConLaUltimaActualizacion() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.LAWN), linkLost = true))
        rule.onNodeWithText("Sin conexión con el robot. Última actualización", substring = true).assertIsDisplayed()
        assertEquals(0, count("Datos antiguos (más de 5 s). No representan el estado actual."))
        assertEquals(0, count("Conexión recuperada.")) // todavía no volvió
    }

    @Test
    fun alRecuperarLaConexionConLaRondaPausadaElBannerVerdePideReanudar() {
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.LAWN), recovered = true))
        rule.onNodeWithText("Conexión recuperada. Reanudá la ronda cuando estés listo.").assertIsDisplayed()
        assertEquals(0, count("Sin conexión con el robot. Última actualización", substring = true))
        rule.onNodeWithText("Reanudar ronda").performScrollTo().assertIsEnabled()
    }

    @Test
    fun alRecuperarLaConexionSinRondaPausadaElAvisoEsCorto() {
        show(uiStateOf(telemetryOf(), recovered = true))
        rule.onNodeWithText("Conexión recuperada.").assertIsDisplayed()
        assertEquals(0, count("Reanudá la ronda", substring = true))
    }

    @Test
    fun sinDatosTodaviaSeAvisaYSoloQuedaLaDetencion() {
        show(uiStateOf(null))
        rule.onNodeWithText("Esperando datos del robot…").assertIsDisplayed()
        rule.onNodeWithText("Sin datos todavía").assertIsDisplayed()
        rule.onNodeWithText("Detención operativa").assertIsEnabled()
    }
}
