package com.uade.robotcierre.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.ui.screens.AlertsScreen
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.viewmodel.AlertUi
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<RobotUiEvent>()

    private fun show(state: UiState) {
        rule.setContent { RobotCierreUadeTheme { AlertsScreen(state, onEvent = { events += it }) } }
    }

    private fun alert(
        id: String, type: EventType, sev: Severity, zone: Zone? = null, text: String, closed: Boolean = false
    ) = AlertUi(RobotEvent(id, type, sev, zone, text), receivedAt = 1_700_000_000_000L, closed = closed)

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun sinAlertasSeAvisaYElBotonQuedaDeshabilitado() {
        show(uiStateOf(telemetryOf()))
        rule.onNodeWithText("No hay alertas.").assertIsDisplayed()
        rule.onNodeWithText("Sin alertas abiertas").assertIsDisplayed()
        rule.onNodeWithText("Reconocer alertas").assertIsNotEnabled()
        rule.onNodeWithText("No hay alertas activas.").assertExists()
    }

    @Test
    fun lasAbiertasVanArribaYLasCerradasAtenuadasAlFinal() {
        val list = listOf(
            alert("E-3", EventType.MISSION_COMPLETE, Severity.INFO, text = "Ronda finalizada.", closed = true),
            alert("E-2", EventType.LOW_BATTERY, Severity.WARNING, text = "Batería al 20 %."),
            alert("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, "Persona detectada en la zona.")
        )
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.PERGOLA), alerts = list))

        rule.onNodeWithText("2 abiertas").assertIsDisplayed()
        rule.onNodeWithText("Persona detectada").assertIsDisplayed()
        rule.onNodeWithText("Batería baja").assertIsDisplayed()
        rule.onNodeWithText("Cerrada").assertExists()
        val persona = rule.onNodeWithText("Persona detectada").getBoundsInRoot().top
        val bateria = rule.onNodeWithText("Batería baja").getBoundsInRoot().top
        val completa = rule.onNodeWithText("Ronda completa").getBoundsInRoot().top
        assertTrue("abiertas antes que cerradas", persona < completa && bateria < completa)
    }

    private val lowBattery = alert(
        "E-2", EventType.LOW_BATTERY, Severity.WARNING,
        text = "Batería al 20 %. Zonas sin visitar: Explanada y rampa."
    )

    @Test
    fun laBateriaBajaOfreceVolverALaBaseYCancelarLaRonda() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.LAWN), alerts = listOf(lowBattery)))
        rule.onNodeWithText("Volver a la base").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("Cancelar ronda").assertIsDisplayed().assertIsEnabled()

        rule.onNodeWithText("Volver a la base").performClick()
        rule.onNodeWithText("Cancelar ronda").performClick()
        assertEquals(
            listOf<RobotUiEvent>(
                RobotUiEvent.RequestCommand(CommandType.RETURN_TO_SAFE_POINT),
                RobotUiEvent.RequestCommand(CommandType.CANCEL_MISSION)
            ),
            events
        )
    }

    @Test
    fun conLaRondaPausadaLasAccionesDeBateriaBajaSiguenDisponibles() {
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.LAWN), alerts = listOf(lowBattery)))
        rule.onNodeWithText("Volver a la base").assertIsEnabled()
        rule.onNodeWithText("Cancelar ronda").assertIsEnabled()
    }

    @Test
    fun sinRondaEnCursoLasAccionesQuedanDeshabilitadasConSuMotivo() {
        show(uiStateOf(telemetryOf(), alerts = listOf(lowBattery)))
        rule.onNodeWithText("Volver a la base").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar ronda").assertIsNotEnabled()
        rule.onNodeWithText("El robot ya está en la base.").assertExists()
        rule.onNodeWithText("La ronda ya finalizó.").assertExists()
    }

    @Test
    fun sinConexionLasAccionesDeBateriaBajaNoSePuedenUsar() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.LAWN), stale = true, alerts = listOf(lowBattery)))
        rule.onNodeWithText("Volver a la base").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar ronda").assertIsNotEnabled()
        assertEquals(2, count("Sin conexión con el robot.") - 1) // una la usa "Reconocer alertas"
    }

    @Test
    fun unaAlertaDeBateriaCerradaOOtrosTiposNoOfrecenEsasAcciones() {
        val closed = lowBattery.copy(closed = true)
        val person = alert("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, "Persona detectada.")
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.PERGOLA), alerts = listOf(closed, person)))
        assertEquals(0, count("Volver a la base"))
        assertEquals(0, count("Cancelar ronda"))
    }

    @Test
    fun elSubtituloConcuerdaEnSingular() {
        val one = alert("E-1", EventType.LOW_BATTERY, Severity.WARNING, text = "Batería al 20 %.")
        show(uiStateOf(telemetryOf(), alerts = listOf(one)))
        rule.onNodeWithText("1 abierta").assertIsDisplayed()
    }

    @Test
    fun laAlertaDePersonaOfreceLasOpcionesDelOperador() {
        val list = listOf(
            alert("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, "Persona detectada en la zona.")
        )
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.PERGOLA), alerts = list))
        rule.onNodeWithText("Elegí: avisar a seguridad, continuar o volver a la base.").assertIsDisplayed()
        rule.onNodeWithText("Zona techada del patio", substring = true).assertExists()
    }

    @Test
    fun reconocerEnviaLaOrdenYUnaAlertaCerradaNoPideOpciones() {
        val open = alert("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, "Persona detectada.")
        show(uiStateOf(telemetryOf(RobotState.PAUSED, Zone.PERGOLA), alerts = listOf(open)))
        rule.onNodeWithText("Reconocer alertas").assertIsEnabled().performClick()
        assertEquals(listOf<RobotUiEvent>(RobotUiEvent.RequestCommand(CommandType.ACK_ALERT)), events)
    }

    @Test
    fun conLaAlertaCerradaYaNoSeOfreceNiElBotonNiLasOpciones() {
        val closed = alert(
            "E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, "Persona detectada.", closed = true
        )
        show(uiStateOf(telemetryOf(), alerts = listOf(closed)))
        assertEquals(0, count("Elegí: avisar a seguridad, continuar o volver a la base."))
        rule.onNodeWithText("Reconocer alertas").assertIsNotEnabled()
    }

    @Test
    fun sinConexionNoSePuedeReconocer() {
        val open = alert("E-1", EventType.CONNECTION_LOST, Severity.CRITICAL, text = "Se perdió la conexión.")
        show(uiStateOf(telemetryOf(), stale = true, alerts = listOf(open)))
        rule.onNodeWithText("Reconocer alertas").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión con el robot.").assertExists()
    }
}
