package com.uade.robotcierre

import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uade.robotcierre.viewmodel.RobotUiEvent
import com.uade.robotcierre.viewmodel.RobotViewModel
import com.uade.robotcierre.viewmodel.SimulationAction
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deja capturas del Panel en la carpeta `files/capturas` de la app (evidencia de la entrega).
 * Para bajarlas: `adb exec-out run-as com.uade.robotcierre cat files/capturas/<nombre>.png`
 * (hay que correr el test con `am instrument`, porque Gradle desinstala la app al terminar).
 */
@RunWith(AndroidJUnit4::class)
class PanelCapturasTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** El Panel conserva el desplazamiento: se vuelve al tope antes de cada captura "de arriba". */
    private fun scrollToTop() {
        repeat(4) { rule.onNode(hasScrollAction()).performTouchInput { swipeDown() } }
        rule.waitForIdle()
    }

    /** Baja el Panel hasta el final: se desplaza a la leyenda de software y a la última orden. */
    private fun scrollToBottom(lastText: String) {
        rule.onNodeWithText("Detención operativa").performScrollTo()
        rule.onNodeWithText(lastText).performScrollTo()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.filesDir, "capturas").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "$name.png").length() > 0)
    }

    @Test
    fun capturasDelPanelEnReposoEnRondaYConDatosAntiguos() {
        // 1. Reposo.
        rule.onNodeWithText("Supervisar").performClick()
        waitForText("Reposo")
        Thread.sleep(1_500)
        scrollToTop()
        shot("panel_1_reposo")
        scrollToBottom("Todavía no enviaste órdenes.")
        shot("panel_1_reposo_abajo")

        // 2. Ronda en curso (ya pasó al segundo punto, con la orden Confirmada).
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        waitForText("Punto 2 de 6 · Pasillo de servicios", timeoutMillis = 45_000)
        scrollToTop()
        shot("panel_2_en_ronda")
        scrollToBottom("Órdenes recientes")
        shot("panel_2_en_ronda_abajo")

        // 3. Desconexión forzada: a los 5 s los datos pasan a "antiguos".
        val container = (rule.activity.application as RobotCierreApplication).container
        val viewModel = ViewModelProvider(rule.activity, container.createRobotViewModelFactory())[RobotViewModel::class.java]
        rule.runOnUiThread { viewModel.onEvent(RobotUiEvent.Simulate(SimulationAction.DISCONNECT)) }
        waitForText("Datos antiguos (más de 5 s). No representan el estado actual.", timeoutMillis = 15_000)
        scrollToTop()
        shot("panel_3_datos_antiguos")
        scrollToBottom("Órdenes recientes")
        shot("panel_3_datos_antiguos_abajo")
    }
}
