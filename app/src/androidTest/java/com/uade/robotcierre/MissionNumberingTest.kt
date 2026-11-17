package com.uade.robotcierre

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Los códigos de ronda continúan desde el último guardado en Room (base vacía y con historial). */
@RunWith(AndroidJUnit4::class)
class MissionNumberingTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        restoreSimulation()
    }

    private fun launchWithHistory(vararg codes: String) {
        val app = ApplicationProvider.getApplicationContext<RobotCierreApplication>()
        runBlocking {
            app.container.clearHistory()
            codes.forEachIndexed { i, code -> app.container.historyRepository.openMission(code, 1_000L + i) }
        }
        app.container.simulationConfig = FastSimulation
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.onNodeWithText("Supervisar").performClick()
        rule.waitForText("Reposo")
        rule.onNodeWithText("Iniciar ronda").performScrollTo().performClick()
        rule.waitForText("En ronda")
        rule.tab("Rondas").performClick()
    }

    @Test
    fun conLaBaseVaciaLaPrimeraRondaEsM001() {
        launchWithHistory()
        rule.waitForText("Ronda M-001")
    }

    @Test
    fun conHistorialLaPrimeraRondaContinuaDesdeElUltimoCodigo() {
        launchWithHistory("M-001", "M-002", "M-005") // la numeración ya llegó a 5
        rule.waitForText("Ronda M-006")
    }
}
