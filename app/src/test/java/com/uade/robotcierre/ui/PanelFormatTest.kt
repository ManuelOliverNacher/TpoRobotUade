package com.uade.robotcierre.ui

import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.ui.screens.estimatedText
import com.uade.robotcierre.ui.screens.movementText
import com.uade.robotcierre.ui.screens.progressText
import com.uade.robotcierre.ui.screens.speedText
import com.uade.robotcierre.ui.screens.verifiedText
import com.uade.robotcierre.viewmodel.testTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelFormatTest {
    @Test
    fun progresoDeLaRonda() {
        assertEquals("Sin ronda en curso", progressText(testTelemetry()))
        assertEquals(
            "Punto 4 de 6 · Zona techada del patio",
            progressText(testTelemetry(state = RobotState.ON_MISSION, zone = Zone.PERGOLA))
        )
        // Volviendo: hay ronda (checkpoint > 0) pero ya no hay zona.
        val volviendo = testTelemetry(state = RobotState.RETURNING, zone = Zone.ESPLANADE)
            .copy(zone = null)
        assertEquals("Volviendo a la base", progressText(volviendo))
    }

    @Test
    fun movimientoVelocidadYZonas() {
        val moving = testTelemetry(state = RobotState.ON_MISSION, zone = Zone.HALL)
            .copy(moving = true, speedMs = 0.8)
        assertEquals("Caminando", movementText(moving))
        assertEquals("0,8 m/s", speedText(moving)) // coma decimal
        assertEquals("Detenido", movementText(testTelemetry()))
        assertEquals("0 de 6", verifiedText(testTelemetry()))
        assertEquals("≈ 5 min restantes", estimatedText(testTelemetry()))
    }
}
