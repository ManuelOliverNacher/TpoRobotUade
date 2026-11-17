package com.uade.robotcierre.ui

import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.ui.screens.simulationAvailability
import com.uade.robotcierre.viewmodel.SimulationAction
import com.uade.robotcierre.viewmodel.UiState
import com.uade.robotcierre.viewmodel.testTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationAvailabilityTest {

    private fun state(t: Telemetry?, connection: ConnectionState = ConnectionState.CONNECTED) =
        UiState(telemetry = t, connectionEffective = connection)

    private fun enabled(a: SimulationAction, s: UiState) = simulationAvailability(a, s).enabled

    @Test
    fun enReposoSoloTienenEfectoLasAccionesGenerales() {
        val s = state(testTelemetry())
        assertTrue(enabled(SimulationAction.DISCONNECT, s))
        assertTrue(enabled(SimulationAction.REJECT_NEXT_COMMAND, s))
        assertTrue(enabled(SimulationAction.LOW_BATTERY, s))
        assertFalse(enabled(SimulationAction.RECONNECT, s))
        assertEquals("Hay conexión.", simulationAvailability(SimulationAction.RECONNECT, s).reason)
        assertEquals(
            "Requiere una ronda en curso.",
            simulationAvailability(SimulationAction.PERSON_DETECTED, s).reason
        )
        assertEquals(
            "Requiere una ronda en curso.",
            simulationAvailability(SimulationAction.ZONE_NOT_VERIFIABLE, s).reason
        )
    }

    @Test
    fun enRondaSePuedeForzarPersonaYZonaNoVerificable() {
        val s = state(testTelemetry(RobotState.ON_MISSION, zone = Zone.PERGOLA))
        assertTrue(enabled(SimulationAction.PERSON_DETECTED, s))
        assertTrue(enabled(SimulationAction.ZONE_NOT_VERIFIABLE, s))
    }

    @Test
    fun elBanoNuncaSePuedeForzarNoVerificable() {
        val s = state(testTelemetry(RobotState.ON_MISSION, zone = Zone.RESTROOM_DOOR))
        assertTrue(enabled(SimulationAction.PERSON_DETECTED, s))
        val a = simulationAvailability(SimulationAction.ZONE_NOT_VERIFIABLE, s)
        assertFalse(a.enabled)
        assertEquals("El baño siempre requiere revisión manual.", a.reason)
    }

    @Test
    fun sinConexionSoloSePuedeReconectarYLasDemasDependenDeLaRonda() {
        val s = state(testTelemetry(RobotState.ON_MISSION, zone = Zone.HALL), ConnectionState.DISCONNECTED)
        assertTrue(enabled(SimulationAction.RECONNECT, s))
        assertFalse(enabled(SimulationAction.DISCONNECT, s))
        assertEquals("Ya está desconectado.", simulationAvailability(SimulationAction.DISCONNECT, s).reason)
        assertEquals("Sin conexión con el robot.", simulationAvailability(SimulationAction.PERSON_DETECTED, s).reason)
        assertEquals(
            "Sin conexión con el robot.",
            simulationAvailability(SimulationAction.ZONE_NOT_VERIFIABLE, s).reason
        )
        assertTrue(enabled(SimulationAction.LOW_BATTERY, s))
        assertTrue(enabled(SimulationAction.REJECT_NEXT_COMMAND, s))
    }

    @Test
    fun sinDatosTodaviaSeConsideraSinConexion() {
        val s = state(null, ConnectionState.DISCONNECTED)
        assertTrue(enabled(SimulationAction.RECONNECT, s))
        assertFalse(enabled(SimulationAction.PERSON_DETECTED, s))
    }

    @Test
    fun cadaAccionTieneSuTextoEnEspanolEnElOrdenDeLaEspecificacion() {
        assertEquals(
            listOf(
                "Forzar desconexión", "Reconectar", "Rechazar próxima orden",
                "Batería baja", "Persona detectada", "Zona no verificable",
                "Forzar falla", "Conexión degradada", "Obstáculo en la ruta"
            ),
            SimulationAction.entries.map { it.label() }
        )
    }

    @Test
    fun fallaYObstaculoRequierenUnaRondaEnCurso() {
        val idle = state(testTelemetry())
        listOf(SimulationAction.FAULT, SimulationAction.OBSTACLE).forEach {
            assertFalse(enabled(it, idle))
            assertEquals("Requiere una ronda en curso.", simulationAvailability(it, idle).reason)
        }
        val running = state(testTelemetry(RobotState.ON_MISSION, zone = Zone.LAWN))
        assertTrue(enabled(SimulationAction.FAULT, running))
        assertTrue(enabled(SimulationAction.OBSTACLE, running))
    }

    @Test
    fun conFallaNoSePuedeForzarOtraFalla() {
        val s = state(testTelemetry(RobotState.FAULT, zone = Zone.LAWN))
        assertEquals("Ya hay una falla.", simulationAvailability(SimulationAction.FAULT, s).reason)
    }

    @Test
    fun conUnObstaculoSinReconocerNoSePuedeForzarOtro() {
        val alert = com.uade.robotcierre.viewmodel.AlertUi(
            com.uade.robotcierre.viewmodel.testEvent(
                type = com.uade.robotcierre.model.EventType.OBSTACLE,
                severity = com.uade.robotcierre.model.Severity.WARNING
            ),
            receivedAt = 0L,
            closed = false
        )
        val s = UiState(
            telemetry = testTelemetry(RobotState.ON_MISSION, zone = Zone.LAWN),
            connectionEffective = ConnectionState.CONNECTED,
            alerts = listOf(alert)
        )
        assertEquals("Ya hay un obstáculo sin reconocer.", simulationAvailability(SimulationAction.OBSTACLE, s).reason)
    }

    @Test
    fun laConexionDegradadaSeForzaConConexionYSeSaleConReconectar() {
        val ok = state(testTelemetry())
        assertTrue(enabled(SimulationAction.DEGRADED_CONNECTION, ok))
        assertFalse(enabled(SimulationAction.RECONNECT, ok))

        val degraded = state(testTelemetry(connection = ConnectionState.DEGRADED), ConnectionState.DEGRADED)
        assertEquals(
            "La conexión ya está degradada.",
            simulationAvailability(SimulationAction.DEGRADED_CONNECTION, degraded).reason
        )
        assertTrue(enabled(SimulationAction.RECONNECT, degraded))

        val lost = state(testTelemetry(), ConnectionState.DISCONNECTED)
        assertEquals(
            "Sin conexión con el robot.",
            simulationAvailability(SimulationAction.DEGRADED_CONNECTION, lost).reason
        )
    }
}
