package com.uade.robotcierre

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flujos del Panel con el ViewModel y el simulador reales (tiempo real). */
@RunWith(AndroidJUnit4::class)
class PanelFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun supervise() {
        rule.onNodeWithText("Supervisar").performClick()
        waitForText("Reposo")
    }

    @Test
    fun iniciarUnaRondaPasaDePendienteAConfirmadaYElRobotEntraEnRonda() {
        supervise()
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        waitForText("Confirmada")
        waitForText("En ronda")
        rule.onNodeWithText("Punto 1 de 6 · Hall").assertExists()
    }

    @Test
    fun cancelarPideConfirmacionYSoloActuaAlConfirmar() {
        supervise()
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        waitForText("En ronda")

        rule.onNodeWithText("Cancelar ronda").performScrollTo().performClick()
        rule.onNodeWithText("¿Cancelar la ronda?").assertIsDisplayed()
        rule.onNodeWithText("El robot volverá a la base.").assertIsDisplayed()
        rule.onNodeWithText("Volver").performClick() // no cancela
        rule.onNodeWithText("¿Cancelar la ronda?").assertDoesNotExist()
        rule.onNodeWithText("En ronda").assertExists()

        rule.onNodeWithText("Cancelar ronda").performScrollTo().performClick()
        rule.onNodeWithText("Confirmar").performClick()
        waitForText("Volviendo")
    }

    @Test
    fun laDetencionOperativaMuestraLaAdvertenciaYDejaAlRobotDetenido() {
        supervise()
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        waitForText("En ronda")

        rule.onNodeWithText("Detención operativa").performClick()
        rule.onNodeWithText("¿Detención operativa?").assertIsDisplayed()
        rule.onNodeWithText(
            "Es un comando de software simulado y no reemplaza una parada de emergencia física."
        ).assertIsDisplayed()
        rule.onNodeWithText("Confirmar").performClick()
        // "Detenido" aparece en la tarjeta Estado y en Movimiento.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Detenido").fetchSemanticsNodes().size >= 2 }
        assertTrue(rule.onAllNodesWithText("Detenido").fetchSemanticsNodes().size >= 2)
    }
}
