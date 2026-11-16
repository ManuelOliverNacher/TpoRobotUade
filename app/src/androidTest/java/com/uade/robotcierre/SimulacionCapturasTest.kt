package com.uade.robotcierre

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.SimulationAction
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Capturas de la hoja de simulación y de los escenarios de la demo, en `files/capturas` de la app.
 * Bajarlas con: `adb exec-out run-as com.uade.robotcierre cat files/capturas/<nombre>.png`
 * (correr con `am instrument`: Gradle desinstala la app al terminar y borra los archivos).
 */
@RunWith(AndroidJUnit4::class)
class SimulacionCapturasTest {

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

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        lateinit var dir: File
        scenario.onActivity { dir = File(it.filesDir, "capturas").apply { mkdirs() } }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "$name.png").length() > 0)
    }

    private fun scrollToTop() {
        repeat(4) { rule.onNode(hasScrollAction()).performTouchInput { swipeDown() } }
        rule.waitForIdle()
    }

    private fun openSheet() {
        rule.onNodeWithContentDescription("Simulación").performClick()
        rule.waitForText("Solo para la demostración.")
    }

    private fun closeSheetWith(label: String) {
        rule.onNode(hasText(label) and hasClickAction()).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Solo para la demostración.").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun capturasDeSimulacionYDesconexion() {
        rule.onNodeWithText("Supervisar").performClick()
        rule.waitForText("Reposo")

        // La hoja en reposo: lo que no tendría efecto queda deshabilitado con su motivo.
        openSheet()
        shot("simulacion_1_hoja_en_reposo")
        Espresso.pressBack()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Solo para la demostración.").fetchSemanticsNodes().isEmpty()
        }

        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")
        openSheet()
        shot("simulacion_2_hoja_en_ronda")
        closeSheetWith("Forzar desconexión")

        // Banner rojo fijo + datos antiguos.
        rule.waitForText("Datos antiguos (más de 5 s). No representan el estado actual.", timeoutMillis = 12_000)
        scrollToTop()
        shot("desconexion_1_banner_rojo_datos_antiguos")

        // La detención no llega: modal y orden "Sin respuesta".
        rule.onNodeWithText("Detención operativa").performScrollTo().performClick()
        rule.waitForText("¿Detención operativa?")
        shot("desconexion_2_modal_detencion")
        rule.onNodeWithText("Confirmar").performClick()
        rule.waitForText("Sin respuesta", timeoutMillis = 20_000)
        rule.onNodeWithText("Sin respuesta").performScrollTo()
        shot("desconexion_3_orden_sin_respuesta")

        // Reconexión: banner verde.
        openSheet()
        closeSheetWith("Reconectar")
        rule.waitForText("Conexión recuperada. Reanudá la ronda cuando estés listo.")
        scrollToTop()
        shot("desconexion_4_conexion_recuperada")
    }

    @Test
    fun capturasDeBateriaBajaYZonaNoVerificable() {
        val vm = { scenario.viewModel() }
        rule.onNodeWithText("Supervisar").performClick()
        rule.waitForText("Reposo")
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("Punto 2 de 6 · Pasillo de servicios", timeoutMillis = 30_000)

        // Escenario 2: batería baja con las zonas que faltan.
        rule.runOnUiThread { vm().onEvent(RobotUiEvent.Simulate(SimulationAction.LOW_BATTERY)) }
        rule.tab("Alertas").performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Zonas sin visitar:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        shot("escenario2_alertas_bateria_baja")

        // Escenario 5: zona no verificable (localización degradada).
        rule.tab("Rondas").performClick()
        rule.waitForText("Punto 5 de 6 · Césped con asientos", timeoutMillis = 30_000)
        rule.runOnUiThread { vm().onEvent(RobotUiEvent.Simulate(SimulationAction.ZONE_NOT_VERIFIABLE)) }
        rule.tab("Panel").performClick()
        rule.waitForText("Degradada")
        scrollToTop()
        shot("escenario5_panel_localizacion_degradada")
        rule.tab("Rondas").performClick()
        rule.waitForText("No verificable")
        scrollToTop()
        shot("escenario5_rondas_zona_no_verificable")

        // Al terminar, el resumen pide la revisión manual de las dos zonas.
        rule.waitForText("RESUMEN DE CIERRE", timeoutMillis = 40_000)
        scrollToTop()
        shot("escenario5_rondas_resumen_con_revision_pendiente")
    }
}
