package com.uade.robotcierre

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.uade.robotcierre.repository.SimulationConfig
import com.uade.robotcierre.viewmodel.RobotViewModel
import kotlinx.coroutines.runBlocking

/** Simulador acelerado: una ronda completa dura unos 20 s en vez de 2 minutos. */
val FastSimulation = SimulationConfig(travelSeconds = 2, verifySeconds = 1, returnSeconds = 2)

/** Prepara la app para una prueba: historial vacío y simulador acelerado, y abre la Activity. */
fun launchFreshApp(config: SimulationConfig = FastSimulation): ActivityScenario<MainActivity> {
    val app = ApplicationProvider.getApplicationContext<RobotCierreApplication>()
    runBlocking { app.container.clearHistory() }
    app.container.simulationConfig = config
    return ActivityScenario.launch(MainActivity::class.java)
}

fun restoreSimulation() {
    ApplicationProvider.getApplicationContext<RobotCierreApplication>().container.simulationConfig =
        SimulationConfig()
}

fun ActivityScenario<MainActivity>.viewModel(): RobotViewModel {
    lateinit var result: RobotViewModel
    onActivity {
        val container = (it.application as RobotCierreApplication).container
        result = ViewModelProvider(it, container.createRobotViewModelFactory())[RobotViewModel::class.java]
    }
    return result
}

fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 15_000) {
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

/** La pestaña de la barra inferior (el título de la pantalla repite el nombre, pero no es clickeable). */
fun ComposeTestRule.tab(name: String): SemanticsNodeInteraction =
    onNode(hasText(name) and hasClickAction())
