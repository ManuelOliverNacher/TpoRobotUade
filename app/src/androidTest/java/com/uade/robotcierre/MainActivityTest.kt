package com.uade.robotcierre

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uade.robotcierre.viewmodel.RobotViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba de la app armada de punta a punta (Activity, ViewModel, simulador y Room reales). */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun viewModelOf(activity: MainActivity): RobotViewModel {
        val container = (activity.application as RobotCierreApplication).container
        return ViewModelProvider(activity, container.createRobotViewModelFactory())[RobotViewModel::class.java]
    }

    @Test
    fun arrancaEnLaSeleccionDeRobotYPasaAlPanelConLas4Pestanas() {
        rule.onNodeWithText("Elegí un robot").assertIsDisplayed()
        rule.onNodeWithText("SIMULADO").assertIsDisplayed()
        rule.onNodeWithText("El robot es simulado. No hay hardware conectado.").assertIsDisplayed()

        rule.onNodeWithText("Supervisar").performClick()
        listOf("Panel", "Rondas", "Alertas", "Historial").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onNodeWithText("Alertas").performClick()
        rule.onNodeWithText("No hay alertas.").assertIsDisplayed()
    }

    @Test
    fun laSeleccionDeRobotMuestraLaConexionYLaBateriaDelSimulador() {
        // El simulador emite desde el primer segundo: aparece "Conectado" y la batería.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTextCount("Conectado") > 0
        }
        rule.onNodeWithText("Conectado").assertIsDisplayed()
    }

    @Test
    fun elViewModelSobreviveARecrearLaActividadYSeMantieneLaPantalla() {
        rule.onNodeWithText("Supervisar").performClick()
        rule.onNodeWithText("Detención operativa").assertIsDisplayed()

        lateinit var before: RobotViewModel
        rule.activityRule.scenario.onActivity { before = viewModelOf(it) }
        rule.activityRule.scenario.recreate()
        rule.activityRule.scenario.onActivity { assertSame(before, viewModelOf(it)) }

        rule.onNodeWithText("Detención operativa").assertIsDisplayed() // sigue en el Panel
    }

    @Test
    fun laOrientacionEstaBloqueadaEnVertical() {
        rule.activityRule.scenario.onActivity {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, it.requestedOrientation)
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
