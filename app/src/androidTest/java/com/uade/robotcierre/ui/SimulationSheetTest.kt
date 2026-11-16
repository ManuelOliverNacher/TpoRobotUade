package com.uade.robotcierre.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.ui.screens.SimulationSheet
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.viewmodel.SimulationAction
import com.uade.robotcierre.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulationSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<SimulationAction>()
    private var dismissed = 0

    private fun show(state: UiState) {
        rule.setContent {
            RobotCierreUadeTheme {
                SimulationSheet(state, onAction = { actions += it }, onDismiss = { dismissed++ })
            }
        }
    }

    @Test
    fun muestraElAvisoDeDemoYLosSeisBotonesEnOrden() {
        show(uiStateOf(telemetryOf()))
        rule.onNodeWithText("Panel de simulación").assertIsDisplayed()
        rule.onNodeWithText("Solo para la demostración.").assertIsDisplayed()
        listOf(
            "Forzar desconexión", "Reconectar", "Rechazar próxima orden",
            "Batería baja", "Persona detectada", "Zona no verificable"
        ).forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun enReposoLasAccionesQueSeIgnorarianQuedanDeshabilitadasConSuMotivo() {
        show(uiStateOf(telemetryOf()))
        rule.onNodeWithText("Persona detectada").assertIsNotEnabled()
        rule.onNodeWithText("Zona no verificable").assertIsNotEnabled()
        rule.onNodeWithText("Reconectar").assertIsNotEnabled()
        rule.onNodeWithText("Hay conexión.").assertExists()
        rule.onNodeWithText("Forzar desconexión").assertIsEnabled()
        rule.onNodeWithText("Batería baja").assertIsEnabled()
        rule.onNodeWithText("Rechazar próxima orden").assertIsEnabled()
    }

    @Test
    fun elegirUnaAccionLaEnviaYCierraLaHoja() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.PERGOLA)))
        rule.onNodeWithText("Persona detectada").assertIsEnabled().performClick()
        assertEquals(listOf(SimulationAction.PERSON_DETECTED), actions)
        assertEquals(1, dismissed)
    }

    @Test
    fun sinConexionSePuedeReconectarPeroNoDesconectarDeNuevo() {
        show(uiStateOf(telemetryOf(RobotState.ON_MISSION, Zone.HALL), stale = true))
        rule.onNodeWithText("Reconectar").assertIsEnabled().performClick()
        assertEquals(listOf(SimulationAction.RECONNECT), actions)
        rule.onNodeWithText("Forzar desconexión").assertIsNotEnabled()
        rule.onNodeWithText("Ya está desconectado.").assertExists()
    }
}
