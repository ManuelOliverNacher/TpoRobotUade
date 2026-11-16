package com.uade.robotcierre.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.ui.screens.HistoryScreen
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<RobotUiEvent>()

    private fun show(state: UiState) {
        rule.setContent { RobotCierreUadeTheme { HistoryScreen(state, onEvent = { events += it }) } }
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private val t0 = 1_700_000_000_000L
    private val missions = listOf(
        MissionRecord(3, "M-003", t0 + 3_000_000, t0 + 3_100_000, MissionResult.COMPLETED, 5),
        MissionRecord(2, "M-002", t0 + 2_000_000, t0 + 2_050_000, MissionResult.CANCELLED, 2),
        MissionRecord(1, "M-001", t0 + 1_000_000, t0 + 1_020_000, MissionResult.INTERRUPTED, 1)
    )

    private fun event(id: Long, mission: Long?, type: EventType, sev: Severity, zone: Zone?, ack: Boolean) =
        EventRecord(id, mission, type, sev, zone, "detalle del evento", t0, ack)

    @Test
    fun sinRondasMuestraElEstadoVacio() {
        show(uiStateOf(null))
        rule.onNodeWithText("Todavía no hay rondas registradas.").assertIsDisplayed()
        rule.onNodeWithText("Sin rondas guardadas").assertIsDisplayed()
    }

    @Test
    fun elSubtituloConcuerdaEnSingularYPlural() {
        show(uiStateOf(telemetryOf(), missions = missions.take(1)))
        rule.onNodeWithText("1 ronda guardada").assertIsDisplayed()
    }

    @Test
    fun listaLasRondasConResultadoYZonasVerificadas() {
        show(uiStateOf(telemetryOf(), missions = missions))
        rule.onNodeWithText("3 rondas guardadas").assertIsDisplayed()
        rule.onNodeWithText("Ronda M-003").assertIsDisplayed()
        rule.onNodeWithText("Completa").assertIsDisplayed()
        rule.onNodeWithText("Cancelada").assertIsDisplayed()
        rule.onNodeWithText("Interrumpida").assertIsDisplayed()
        rule.onNodeWithText("Zonas verificadas: 5 de 6").assertIsDisplayed()
        rule.onNodeWithText("Zonas verificadas: 2 de 6").assertIsDisplayed()
        assertEquals(3, count("Tocá para ver los eventos"))
    }

    @Test
    fun tocarUnaRondaPideSusEventosYTocarLaElegidaLaCierra() {
        show(uiStateOf(telemetryOf(), missions = missions))
        rule.onNodeWithText("Ronda M-002").performClick()
        assertEquals(listOf<RobotUiEvent>(RobotUiEvent.SelectMission(2)), events)
    }

    @Test
    fun laRondaElegidaMuestraSusEventosDebajo() {
        val evs = listOf(
            event(10, 3, EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA, ack = true),
            event(11, 3, EventType.ZONE_NOT_VERIFIABLE, Severity.INFO, Zone.RESTROOM_DOOR, ack = false)
        )
        show(uiStateOf(telemetryOf(), missions = missions, selectedMissionId = 3, selectedMissionEvents = evs))
        rule.onNodeWithText("Persona detectada").assertIsDisplayed()
        rule.onNodeWithText("Zona no verificable").assertIsDisplayed()
        rule.onNodeWithText("Reconocida").assertIsDisplayed()
        rule.onNodeWithText("Abierta").assertIsDisplayed()
        assertEquals(2, count("Tocá para ver los eventos")) // las otras dos siguen cerradas

        rule.onNodeWithText("Ronda M-003").performClick()
        assertEquals(listOf<RobotUiEvent>(RobotUiEvent.SelectMission(null)), events)
    }

    @Test
    fun unaRondaSinEventosLoDice() {
        show(uiStateOf(telemetryOf(), missions = missions, selectedMissionId = 2, selectedMissionEvents = emptyList()))
        rule.onNodeWithText("Sin eventos en esta ronda.").assertIsDisplayed()
    }

    @Test
    fun losEventosFueraDeUnaRondaTienenSuSeccion() {
        val outside = listOf(event(20, null, EventType.CONNECTION_LOST, Severity.CRITICAL, null, ack = true))
        show(uiStateOf(telemetryOf(), missions = missions, eventsOutsideMissions = outside))
        rule.onNodeWithText("Conexión perdida").assertExists()
    }

    @Test
    fun sinRondasPeroConEventosFueraDeRondaSeMuestranAmbos() {
        val outside = listOf(event(20, null, EventType.CONNECTION_LOST, Severity.CRITICAL, null, ack = true))
        show(uiStateOf(telemetryOf(), eventsOutsideMissions = outside))
        rule.onNodeWithText("Todavía no hay rondas registradas.").assertIsDisplayed()
        rule.onNodeWithText("Eventos fuera de una ronda").assertIsDisplayed()
        rule.onNodeWithText("Conexión perdida").assertIsDisplayed()
    }
}
