package com.uade.robotcierre.repository

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Telemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedRobotRepositoryTest {

    private fun TestScope.repo() =
        SimulatedRobotRepository(backgroundScope, clock = { currentTime })

    private fun cmd(id: String, type: CommandType, expires: Int = 10) =
        Command(id, type, 0L, "op-1", expires)

    @Test
    fun primerPaqueteDeTelemetriaEsLaSecuencia1EnReposo() = runTest {
        val t = repo().telemetry.first()
        assertEquals(1L, t.sequence)
        assertEquals(RobotState.IDLE, t.state)
    }

    @Test
    fun laRespuestaTardaEntre1y2SegundosDeterministaYCiclica() = runTest {
        val repo = repo()
        val latencies = (1..5).map { i ->
            val t0 = currentTime
            repo.sendCommand(cmd("C$i", CommandType.ACK_ALERT))
            currentTime - t0
        }
        assertEquals(listOf(1000L, 1250L, 1500L, 1750L, 2000L), latencies)
        assertTrue(latencies.all { it in 1000L..2000L })
    }

    @Test
    fun sinConexionLaOrdenTerminaEnNoResponseAlVencerElPlazo() = runTest {
        val repo = repo()
        repo.forceDisconnect()
        val t0 = currentTime
        val r = repo.sendCommand(cmd("C1", CommandType.START_MISSION, expires = 10))
        assertEquals(CommandStatus.NO_RESPONSE, r.status)
        assertEquals(10_000L, currentTime - t0)

        // Al reconectar vuelve a responder.
        repo.forceReconnect()
        assertEquals(
            CommandStatus.CONFIRMED,
            repo.sendCommand(cmd("C2", CommandType.START_MISSION)).status
        )
    }

    @Test
    fun rejectNextCommandRechazaSoloLaProximaOrden() = runTest {
        val repo = repo()
        repo.rejectNextCommand()
        assertEquals(
            CommandStatus.REJECTED,
            repo.sendCommand(cmd("C1", CommandType.START_MISSION)).status
        )
        assertEquals(
            CommandStatus.CONFIRMED,
            repo.sendCommand(cmd("C2", CommandType.START_MISSION)).status
        )
    }

    @Test
    fun laTelemetriaSeCortaSinConexionYRetomaLaSecuencia() = runTest {
        val repo = repo()
        val received = mutableListOf<Telemetry>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.telemetry.toList(received)
        }
        advanceTimeBy(5_500) // paquetes en t = 0, 1, 2, 3, 4 y 5 s
        assertEquals(6, received.size)

        repo.forceDisconnect()
        advanceTimeBy(10_000)
        assertEquals(6, received.size) // sin paquetes: la UI verá el dato como antiguo

        repo.forceReconnect()
        advanceTimeBy(2_000)
        assertEquals(8, received.size)
        assertEquals((1L..8L).toList(), received.map { it.sequence })
    }

    @Test
    fun losEventosLlegaranPorElFlujoDeEventos() = runTest {
        val repo = repo()
        val events = mutableListOf<RobotEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.events.toList(events)
        }
        assertEquals(
            CommandStatus.CONFIRMED,
            repo.sendCommand(cmd("C1", CommandType.START_MISSION)).status
        )
        repo.forceLowBattery()
        advanceTimeBy(5_000)
        assertEquals(1, events.count { it.type == EventType.LOW_BATTERY })
    }

    @Test
    fun lasRondasContinuanDesdeElUltimoNumeroGuardado() = runTest {
        val repo = SimulatedRobotRepository(backgroundScope, clock = { currentTime }, lastMissionNumber = 7)
        repo.sendCommand(cmd("C1", CommandType.START_MISSION))
        advanceTimeBy(1_500)
        assertEquals("M-008", repo.telemetry.first().missionId)
    }

    @Test
    fun conLaBaseVaciaLaPrimeraRondaEsM001() = runTest {
        val repo = repo()
        repo.sendCommand(cmd("C1", CommandType.START_MISSION))
        advanceTimeBy(1_500)
        assertEquals("M-001", repo.telemetry.first().missionId)
    }
}
