package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.repository.SimulatedRobotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RobotViewModelHistoryTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newVm(repo: FakeRobotRepository, history: FakeHistoryRepository): RobotViewModel {
        val vm = RobotViewModel(repo, null, history, { currentTime })
        runCurrent()
        return vm
    }

    private fun TestScope.tick(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun losEventosSeGuardanConLaRondaYAlReconocerSeMarcanEnElHistorial() = runTest {
        val repo = FakeRobotRepository()
        val history = FakeHistoryRepository()
        val vm = newVm(repo, history)
        repo.telemetryFlow.tryEmit(
            testTelemetry(state = RobotState.PAUSED, zone = Zone.PERGOLA, activeAlerts = 1, timestamp = 0)
        )
        runCurrent()
        repo.eventsFlow.tryEmit(testEvent("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA))
        runCurrent()

        val saved = history.eventsNow.single()
        assertEquals(history.missionsNow.single().id, saved.missionId)
        assertFalse(saved.acknowledged)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.ACK_ALERT))
        runCurrent()
        assertTrue(history.eventsNow.single().acknowledged)
    }

    @Test
    fun elHistorialSeLeeAtravesDelViewModelYSePuedeElegirUnaRonda() = runTest {
        val repo = FakeRobotRepository()
        val history = FakeHistoryRepository()
        val vm = newVm(repo, history)
        repo.telemetryFlow.tryEmit(testTelemetry(state = RobotState.ON_MISSION, zone = Zone.HALL, timestamp = 0))
        runCurrent()
        repo.eventsFlow.tryEmit(testEvent("E-1", EventType.LOW_BATTERY, Severity.WARNING))
        repo.eventsFlow.tryEmit(testEvent("E-2", EventType.OBSTACLE, Severity.WARNING))
        runCurrent()

        val mission = vm.uiState.value.missions.single()
        assertEquals("M-001", mission.code)
        assertTrue(vm.uiState.value.selectedMissionEvents.isEmpty())

        vm.onEvent(RobotUiEvent.SelectMission(mission.id))
        runCurrent()
        assertEquals(mission.id, vm.uiState.value.selectedMissionId)
        assertEquals(
            listOf(EventType.LOW_BATTERY, EventType.OBSTACLE),
            vm.uiState.value.selectedMissionEvents.map { it.type }
        )

        vm.onEvent(RobotUiEvent.SelectMission(null))
        runCurrent()
        assertNull(vm.uiState.value.selectedMissionId)
        assertTrue(vm.uiState.value.selectedMissionEvents.isEmpty())
    }

    @Test
    fun losEventosFueraDeUnaRondaTambienSeExponen() = runTest {
        val repo = FakeRobotRepository()
        val history = FakeHistoryRepository()
        val vm = newVm(repo, history)
        repo.eventsFlow.tryEmit(testEvent("E-1", EventType.CONNECTION_LOST, Severity.CRITICAL))
        runCurrent()
        assertEquals(EventType.CONNECTION_LOST, vm.uiState.value.eventsOutsideMissions.single().type)
        assertTrue(vm.uiState.value.missions.isEmpty())
    }

    @Test
    fun rondaNormalConElSimuladorQuedaCompletaYLaRevisionDelBanoLaCompleta() = runTest {
        val sim = SimulatedRobotRepository(backgroundScope, clock = { currentTime })
        val history = FakeHistoryRepository()
        val vm = RobotViewModel(sim, sim, history, { currentTime })
        runCurrent()
        tick(1_500)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        tick(1_100)
        var guard = 0
        while (history.missionsNow.firstOrNull()?.result != MissionResult.COMPLETED) {
            check(guard++ < 400) { "La ronda no terminó" }
            tick(1_000)
        }

        val mission = history.missionsNow.single()
        assertEquals(5, mission.zonesVerified) // el baño espera revisión manual
        val events = history.eventsNow.filter { it.missionId == mission.id }
        val restroom = events.single { it.type == EventType.ZONE_NOT_VERIFIABLE }
        assertEquals(Zone.RESTROOM_DOOR, restroom.zone)
        assertFalse(restroom.acknowledged)
        assertTrue(events.single { it.type == EventType.MISSION_COMPLETE }.acknowledged)
        assertEquals(mission.id, vm.uiState.value.missions.single().id)

        // El operador registra la revisión manual del baño ya terminada la ronda.
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, Zone.RESTROOM_DOOR))
        tick(1_400)
        tick(2_000)
        assertEquals(CommandStatus.CONFIRMED, vm.uiState.value.commands.first().status)
        assertEquals(
            ZoneStatus.VERIFIED_MANUALLY,
            vm.uiState.value.telemetry!!.zonesStatus[Zone.RESTROOM_DOOR]
        )
        assertEquals(6, history.missionsNow.single().zonesVerified)
        assertTrue(history.eventsNow.single { it.type == EventType.ZONE_NOT_VERIFIABLE }.acknowledged)
    }

    @Test
    fun cancelarUnaRondaConElSimuladorQuedaCancelled() = runTest {
        val sim = SimulatedRobotRepository(backgroundScope, clock = { currentTime })
        val history = FakeHistoryRepository()
        val vm = RobotViewModel(sim, sim, history, { currentTime })
        runCurrent()
        tick(1_500)
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        tick(1_100)
        tick(30_000) // ya avanzó por el Hall y el Pasillo

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.CANCEL_MISSION))
        assertEquals(CommandType.CANCEL_MISSION, vm.uiState.value.confirmation)
        vm.onEvent(RobotUiEvent.ConfirmPending)
        tick(1_400)
        assertEquals(MissionResult.IN_PROGRESS, history.missionsNow.single().result) // vuelve a la base

        var guard = 0
        while (history.missionsNow.single().result == MissionResult.IN_PROGRESS) {
            check(guard++ < 100) { "No volvió a la base" }
            tick(1_000)
        }
        val mission = history.missionsNow.single()
        assertEquals(MissionResult.CANCELLED, mission.result)
        assertTrue(mission.zonesVerified in 1..3)
    }
}
