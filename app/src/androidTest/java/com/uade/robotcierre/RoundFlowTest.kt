package com.uade.robotcierre

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.SimulationAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Escenarios completos de punta a punta (Activity, ViewModel, simulador acelerado y Room reales). */
@RunWith(AndroidJUnit4::class)
class RoundFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = launchFreshApp()
    }

    @After
    fun tearDown() {
        scenario.close()
        restoreSimulation()
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countUnmerged(text: String) =
        rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun startRound() {
        rule.onNodeWithText("Supervisar").performClick()
        rule.waitForText("Reposo")
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")
    }

    @Test
    fun misionNormalTerminaConResumenRevisionManualEHistorial() {
        startRound()
        rule.tab("Rondas").performClick()
        rule.waitForText("Ronda M-001")

        // La ronda termina sola: aparece el resumen de cierre con el baño pendiente.
        rule.waitForText("RESUMEN DE CIERRE", timeoutMillis = 60_000)
        rule.onNodeWithText("Ronda completa.").assertIsDisplayed()
        rule.onNodeWithText("Zonas libres: 5 de 6.").assertIsDisplayed()
        rule.onNodeWithText("Puerta del baño: requiere revisión manual.").assertIsDisplayed()

        rule.onNodeWithText("Registrar revisión manual").performClick()
        rule.waitForText("Zonas libres: 6 de 6.", timeoutMillis = 20_000)
        assertEquals(0, count("Registrar revisión manual"))

        // El historial quedó guardado: una ronda completa con las 6 zonas verificadas.
        rule.tab("Historial").performClick()
        rule.waitForText("Zonas verificadas: 6 de 6")
        rule.onNodeWithText("Ronda M-001").assertIsDisplayed()
        rule.onNodeWithText("Completa").assertIsDisplayed()
        rule.onNodeWithText("Ronda M-001").performClick()
        // Aviso del baño (cerrado por la revisión) y fin de ronda: los dos dentro de la ronda.
        // La tarjeta es clickeable y fusiona sus textos: se cuentan los chips en el árbol sin fusionar.
        rule.waitUntil(10_000) { countUnmerged("Reconocida") == 2 }
        assertEquals(0, count("Eventos fuera de una ronda"))
    }

    @Test
    fun incidenteDePersonaSeReconoceYLaRondaContinua() {
        startRound()
        rule.waitForText("Punto 4 de 6 · Zona techada del patio", timeoutMillis = 30_000)
        scenario.viewModel().let { vm ->
            rule.runOnUiThread { vm.onEvent(RobotUiEvent.Simulate(SimulationAction.PERSON_DETECTED)) }
        }
        rule.waitForText("Persona detectada en Zona techada del patio")

        rule.tab("Alertas").performClick()
        rule.waitForText("Elegí: avisar a seguridad, continuar o volver a la base.")
        rule.onNodeWithText("Reconocer alertas").performClick()
        rule.waitForText("Cerrada")

        rule.tab("Panel").performClick()
        rule.waitForText("Pausado")
        rule.onNodeWithText("Reanudar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")

        rule.tab("Rondas").performClick()
        rule.waitForText("RESUMEN DE CIERRE", timeoutMillis = 60_000)
        rule.onNodeWithText("Zona techada del patio: se encontró una persona.").assertIsDisplayed()
    }
}
