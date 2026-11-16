package com.uade.robotcierre.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.screens.RoundsScreen
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<RobotUiEvent>()

    private fun show(state: UiState) {
        rule.setContent { RobotCierreUadeTheme { RoundsScreen(state, onEvent = { events += it }) } }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private val closedStatuses = Zone.entries.associateWith { ZoneStatus.FREE }.toMutableMap()
        .apply { put(Zone.RESTROOM_DOOR, ZoneStatus.MANUAL_REVIEW) }

    @Test
    fun sinRondaInvitaAIniciarYMuestraLasSeisZonasEnOrden() {
        show(uiStateOf(telemetryOf()))
        rule.onNodeWithText("Sin ronda en curso").assertIsDisplayed()
        rule.onNodeWithText("Iniciá una ronda desde el Panel.").assertIsDisplayed()
        listOf(
            "Hall", "Pasillo de servicios", "Puerta del baño",
            "Zona techada del patio", "Césped con asientos", "Explanada y rampa"
        ).forEach { rule.onNodeWithText(it).performScrollTo().assertExists() }
        assertEquals(6, count("Pendiente"))
        assertEquals(0, count("ZONA ACTUAL"))
    }

    @Test
    fun enRondaMuestraElProgresoLaZonaActualYLosEstados() {
        val statuses = Zone.entries.associateWith { ZoneStatus.PENDING }.toMutableMap().apply {
            put(Zone.HALL, ZoneStatus.FREE); put(Zone.CORRIDOR, ZoneStatus.FREE)
        }
        val mission = MissionRecord(1, "M-001", 1_700_000_000_000L, null, MissionResult.IN_PROGRESS, 2)
        show(
            uiStateOf(
                telemetryOf(RobotState.ON_MISSION, Zone.RESTROOM_DOOR, statuses = statuses),
                missions = listOf(mission)
            )
        )
        rule.onNodeWithText("Ronda M-001").assertIsDisplayed()
        rule.onNodeWithText("Inicio", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Punto 3 de 6 · Puerta del baño").assertIsDisplayed()
        assertEquals(1, count("ZONA ACTUAL"))
        assertEquals(2, count("Libre"))
        assertEquals(0, count("Resumen de cierre"))
    }

    @Test
    fun alTerminarMuestraElResumenDeCierreYPermiteRegistrarLaRevision() {
        val mission = MissionRecord(1, "M-001", 1_700_000_000_000L, 1_700_000_100_000L, MissionResult.COMPLETED, 5)
        show(uiStateOf(telemetryOf(statuses = closedStatuses), missions = listOf(mission)))
        rule.onNodeWithText("RESUMEN DE CIERRE").assertIsDisplayed()
        rule.onNodeWithText("Ronda completa.").assertIsDisplayed()
        rule.onNodeWithText("Zonas libres: 5 de 6.").assertIsDisplayed()
        rule.onNodeWithText("Puerta del baño: requiere revisión manual.").assertIsDisplayed()
        rule.onNodeWithText("Zona: Puerta del baño").assertExists()

        rule.onNodeWithText("Registrar revisión manual").assertIsEnabled().performClick()
        assertEquals(
            listOf(RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, Zone.RESTROOM_DOOR)),
            events
        )
    }

    @Test
    fun sinConexionElBotonDeRevisionQuedaDeshabilitadoConSuMotivo() {
        val mission = MissionRecord(1, "M-001", 1L, 2L, MissionResult.COMPLETED, 5)
        show(uiStateOf(telemetryOf(statuses = closedStatuses), stale = true, missions = listOf(mission)))
        rule.onNodeWithText("Registrar revisión manual").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión con el robot.").assertExists()
        rule.onNodeWithText("Datos antiguos (más de 5 s). No representan el estado actual.").assertIsDisplayed()
        assertEquals(7, count("ANTIGUO")) // la tarjeta de ronda actual y las 6 zonas
    }

    @Test
    fun conLaRevisionHechaElResumenYaNoOfreceElBoton() {
        val ok = closedStatuses.toMutableMap().apply { put(Zone.RESTROOM_DOOR, ZoneStatus.VERIFIED_MANUALLY) }
        val mission = MissionRecord(1, "M-001", 1L, 2L, MissionResult.COMPLETED, 6)
        show(uiStateOf(telemetryOf(statuses = ok), missions = listOf(mission)))
        rule.onNodeWithText("Zonas libres: 6 de 6.").assertIsDisplayed()
        assertEquals(0, count("Registrar revisión manual"))
        rule.onNodeWithText("Verificada manualmente").performScrollTo().assertExists()
    }

    @Test
    fun unaRondaIncompletaListaLasZonasPendientes() {
        val partial = Zone.entries.associateWith { ZoneStatus.PENDING }.toMutableMap()
            .apply { put(Zone.HALL, ZoneStatus.FREE) }
        val mission = MissionRecord(1, "M-001", 1L, 2L, MissionResult.RETURNED_EARLY, 1)
        show(uiStateOf(telemetryOf(statuses = partial), missions = listOf(mission)))
        rule.onNodeWithText("Ronda incompleta: el robot volvió a la base antes de terminar.").assertIsDisplayed()
        rule.onNodeWithText("Zonas pendientes:", substring = true).assertIsDisplayed()
        assertEquals(0, count("Registrar revisión manual"))
    }
}
