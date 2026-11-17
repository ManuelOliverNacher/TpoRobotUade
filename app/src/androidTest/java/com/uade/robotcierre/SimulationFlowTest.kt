package com.uade.robotcierre

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.SimulationAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Los escenarios de la demo, accionados desde la hoja de simulación (simulador acelerado). */
@RunWith(AndroidJUnit4::class)
class SimulationFlowTest {

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

    private fun waitForSubstring(text: String, timeoutMillis: Long = 15_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun supervise() {
        rule.onNodeWithText("Supervisar").performClick()
        rule.waitForText("Reposo")
    }

    private fun startRound() {
        supervise()
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")
    }

    /** Abre la hoja, toca la acción y espera a que se cierre. */
    private fun simulate(label: String) {
        rule.onNodeWithContentDescription("Simulación").performClick()
        rule.waitForText("Solo para la demostración.")
        // Con 9 botones la hoja se desplaza (a 360 dp los últimos quedan fuera de pantalla).
        rule.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(10_000) { count("Solo para la demostración.") == 0 }
    }

    @Test
    fun elIconoDeSimulacionEstaEnTodasLasPestanas() {
        supervise()
        listOf("Panel", "Rondas", "Alertas", "Historial").forEach { name ->
            rule.tab(name).performClick()
            rule.onNodeWithContentDescription("Simulación").assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("Simulación").performClick()
        rule.waitForText("Solo para la demostración.")
    }

    @Test
    fun desconexionDatosAntiguosOrdenSinRespuestaYRecuperacion() {
        startRound()
        simulate("Forzar desconexión")

        // Banner rojo fijo y, pasados 5 s, datos antiguos.
        waitForSubstring("Sin conexión con el robot. Última actualización")
        rule.waitForText("Datos antiguos (más de 5 s). No representan el estado actual.", timeoutMillis = 12_000)
        rule.onNodeWithText("Detención operativa").assertIsDisplayed()
        rule.onNodeWithText("Puede quedar sin respuesta.").assertExists()

        // La detención no llega: la orden queda "Sin respuesta" a los 10 s.
        rule.onNodeWithText("Detención operativa").performClick()
        rule.onNodeWithText("¿Detención operativa?").assertIsDisplayed()
        rule.onNodeWithText("Confirmar").performClick()
        rule.waitForText("Sin respuesta", timeoutMillis = 20_000)

        // Al reconectar: banner verde y la ronda sigue en pausa hasta que el operador la reanude.
        simulate("Reconectar")
        rule.waitForText("Conexión recuperada. Reanudá la ronda cuando estés listo.")
        rule.waitForText("Pausado")
        rule.onNodeWithText("Reanudar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")
        assertEquals(0, count("Conexión recuperada. Reanudá la ronda cuando estés listo."))
    }

    @Test
    fun rechazarLaProximaOrdenMuestraElMotivoYLaSiguienteSiSeConfirma() {
        supervise()
        simulate("Rechazar próxima orden")
        // Aviso breve de que quedó armado (se retira solo a los 4 s).
        rule.waitForText("La próxima orden será rechazada.")
        rule.waitUntil(10_000) { count("La próxima orden será rechazada.") == 0 }
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("Rechazada")
        rule.waitForText("Orden rechazada por el robot (simulación).")
        assertEquals(1, count("Reposo")) // no empezó

        // "Iniciar ronda" también figura en la lista de órdenes (la rechazada): se busca el botón.
        rule.onNode(hasText("Iniciar ronda") and hasClickAction()).performScrollTo().performClick()
        rule.waitForText("En ronda")
        rule.onNodeWithText("Confirmada").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bateriaBajaAvisaYListaLasZonasQueFaltan() {
        startRound()
        simulate("Batería baja")
        rule.tab("Alertas").performClick()
        waitForSubstring("Zonas sin visitar:", timeoutMillis = 20_000)
        rule.onNodeWithText("Batería baja").assertIsDisplayed()
        rule.onNodeWithText("Explanada y rampa", substring = true).assertExists()
    }

    @Test
    fun bateriaBajaPermiteVolverALaBaseDesdeLaAlerta() {
        startRound()
        simulate("Batería baja")
        rule.tab("Alertas").performClick()
        waitForSubstring("Zonas sin visitar:", timeoutMillis = 20_000)

        rule.onNodeWithText("Volver a la base").performClick()
        // El robot regresa y la ronda queda como "Volvió antes de terminar".
        rule.tab("Historial").performClick()
        rule.waitForText("Volvió antes de terminar", timeoutMillis = 30_000)
    }

    @Test
    fun bateriaBajaPermiteCancelarLaRondaConConfirmacion() {
        startRound()
        simulate("Batería baja")
        rule.tab("Alertas").performClick()
        waitForSubstring("Zonas sin visitar:", timeoutMillis = 20_000)

        rule.onNodeWithText("Cancelar ronda").performClick()
        rule.onNodeWithText("¿Cancelar la ronda?").assertIsDisplayed()
        rule.onNodeWithText("Volver").performClick() // no cancela
        rule.onNodeWithText("¿Cancelar la ronda?").assertDoesNotExist()
        rule.onNodeWithText("Cancelar ronda").assertIsDisplayed()

        rule.onNodeWithText("Cancelar ronda").performClick()
        rule.onNodeWithText("Confirmar").performClick()
        rule.tab("Historial").performClick()
        rule.waitForText("Cancelada", timeoutMillis = 30_000)
    }

    @Test
    fun unaZonaNoVerificableDegradaLaLocalizacionYSeResuelveConRevisionManual() {
        startRound()
        rule.waitForText("Punto 5 de 6 · Césped con asientos", timeoutMillis = 30_000)
        rule.runOnUiThread {
            scenario.viewModel().onEvent(RobotUiEvent.Simulate(SimulationAction.ZONE_NOT_VERIFIABLE))
        }
        rule.waitForText("Degradada")
        rule.tab("Rondas").performClick()
        rule.waitForText("No verificable")

        // Al terminar: baño y césped esperan revisión manual; se registran las dos.
        rule.waitForText("RESUMEN DE CIERRE", timeoutMillis = 40_000)
        rule.onNodeWithText("Césped con asientos: requiere revisión manual.").assertExists()
        rule.onNodeWithText("Registrar revisión manual").performClick()
        rule.waitForText("Zonas libres: 5 de 6.", timeoutMillis = 20_000)
        rule.onNodeWithText("Registrar revisión manual").performClick()
        rule.waitForText("Zonas libres: 6 de 6.", timeoutMillis = 20_000)
        assertEquals(0, count("Registrar revisión manual"))
    }

    @Test
    fun unaFallaDejaAlRobotEnFallaYSeSaleConDetencionYVueltaALaBase() {
        startRound()
        simulate("Forzar falla")
        rule.waitForText("Falla")
        // Con falla no se puede iniciar, volver ni cancelar; la detención sigue disponible.
        rule.onNode(hasText("Volver a la base") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        assertTrue(count("El robot tiene una falla.") >= 2)
        rule.onNodeWithText("Detención operativa").assertIsEnabled()

        rule.onNodeWithText("Detención operativa").performClick()
        rule.onNodeWithText("Confirmar").performClick()
        // "Detenido" ya figura en Movimiento durante la falla: se espera a que la orden se confirme
        // y "Volver a la base" quede habilitado (STOPPED sí permite volver).
        val back = hasText("Volver a la base") and hasClickAction()
        rule.waitUntil(15_000) { runCatching { rule.onNode(back).assertIsEnabled() }.isSuccess }
        rule.onNode(back).performScrollTo().performClick()
        rule.waitForText("Reposo", timeoutMillis = 30_000)
    }

    @Test
    fun unObstaculoBloqueaVolverYCancelarHastaReconocerLaAlerta() {
        startRound()
        simulate("Obstáculo en la ruta")
        waitForSubstring("Obstáculo en")
        rule.onNode(hasText("Volver a la base") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        rule.onNode(hasText("Cancelar ronda") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        assertTrue(count("Ruta bloqueada por un obstáculo.") >= 2)

        // Reconocer la alerta libera la ruta.
        rule.tab("Alertas").performClick()
        rule.onNodeWithText("Reconocer alertas").performClick()
        rule.tab("Panel").performClick()
        rule.waitUntil(10_000) {
            count("Ruta bloqueada por un obstáculo.") == 0
        }
        rule.onNode(hasText("Volver a la base") and hasClickAction()).performScrollTo().assertIsEnabled()
    }

    @Test
    fun laConexionDegradadaSeVeEnElChipYNoDejaIniciarHastaReconectar() {
        supervise()
        simulate("Conexión degradada")
        rule.waitForText("Degradada")
        rule.onNode(hasText("Iniciar ronda") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Conexión degradada: no se puede iniciar una ronda.").assertExists()

        simulate("Reconectar")
        rule.waitForText("Conectado")
        rule.onNode(hasText("Iniciar ronda") and hasClickAction()).performScrollTo().assertIsEnabled()
    }
}
