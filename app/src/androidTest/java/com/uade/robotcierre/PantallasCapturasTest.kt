package com.uade.robotcierre

import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
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
 * Deja capturas de Rondas, Alertas e Historial en `files/capturas` de la app (evidencia de la entrega).
 * Bajarlas con: `adb exec-out run-as com.uade.robotcierre cat files/capturas/<nombre>.png`
 * (correr con `am instrument`: Gradle desinstala la app al terminar y borra los archivos).
 */
@RunWith(AndroidJUnit4::class)
class PantallasCapturasTest {

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
        Thread.sleep(400)
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

    @Test
    fun capturasDeRondasAlertasEHistorial() {
        rule.onNodeWithText("Supervisar").performClick()
        rule.waitForText("Reposo")

        // Historial vacío (la base se limpió al arrancar).
        rule.tab("Historial").performClick()
        rule.waitForText("Todavía no hay rondas registradas.")
        shot("historial_0_vacio")

        // Ronda en curso.
        rule.tab("Panel").performClick()
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("Confirmada")
        rule.tab("Rondas").performClick()
        rule.waitForText("Punto 3 de 6 · Puerta del baño", timeoutMillis = 30_000)
        shot("rondas_1_en_curso")

        // Persona detectada en el patio.
        rule.waitForText("Punto 4 de 6 · Zona techada del patio", timeoutMillis = 30_000)
        val vm = scenario.viewModel()
        rule.runOnUiThread { vm.onEvent(RobotUiEvent.Simulate(SimulationAction.PERSON_DETECTED)) }
        rule.tab("Alertas").performClick()
        rule.waitForText("Elegí: avisar a seguridad, continuar o volver a la base.")
        shot("alertas_1_persona_abierta")

        rule.onNodeWithText("Reconocer alertas").performClick()
        rule.waitForText("Cerrada")
        shot("alertas_2_reconocidas")

        // Reanuda y termina la ronda: resumen de cierre.
        rule.tab("Panel").performClick()
        rule.waitForText("Pausado")
        rule.onNodeWithText("Reanudar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")
        rule.tab("Rondas").performClick()
        rule.waitForText("RESUMEN DE CIERRE", timeoutMillis = 60_000)
        scrollToTop()
        shot("rondas_2_resumen_cierre")

        rule.onNodeWithText("Registrar revisión manual").performClick()
        rule.waitForText("Zonas libres: 5 de 6.", timeoutMillis = 20_000)
        scrollToTop()
        shot("rondas_3_tras_revision_manual")

        // Historial con la ronda y sus eventos.
        rule.tab("Historial").performClick()
        rule.waitForText("Zonas verificadas: 5 de 6")
        rule.onNodeWithText("Ronda M-001").performClick()
        rule.waitForText("Persona detectada")
        shot("historial_1_ronda_con_eventos")
    }
}
