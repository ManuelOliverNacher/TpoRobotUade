package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.repository.SimulatedRobotRepository
import com.uade.robotcierre.repository.SimulationControls
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RobotViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newVm(
        repo: FakeRobotRepository,
        sim: SimulationControls? = null
    ): RobotViewModel {
        val vm = RobotViewModel(repo, sim, clock = { currentTime })
        runCurrent() // arrancan los colectores de init
        return vm
    }

    private fun TestScope.push(repo: FakeRobotRepository, t: com.uade.robotcierre.model.Telemetry) {
        repo.telemetryFlow.tryEmit(t)
        runCurrent()
    }

    private fun TestScope.pushEvent(repo: FakeRobotRepository, e: com.uade.robotcierre.model.RobotEvent) {
        repo.eventsFlow.tryEmit(e)
        runCurrent()
    }

    private fun RobotViewModel.avail(type: CommandType) = uiState.value.availability.getValue(type)

    // ----------------------------------------------------------- suscripción

    @Test
    fun losEventosSeRecibenDesdeLaCreacionAunqueNadieObserveElEstado() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        pushEvent(repo, testEvent(id = "E-007", type = EventType.OBSTACLE, severity = Severity.WARNING))
        val alerts = vm.uiState.value.alerts
        assertEquals(1, alerts.size)
        assertEquals("E-007", alerts[0].event.eventId)
        assertFalse(alerts[0].closed)
    }

    @Test
    fun conUnPaqueteReplayadoElViewModelNaceConDatos() = runTest {
        val repo = FakeRobotRepository()
        repo.telemetryFlow.tryEmit(testTelemetry(timestamp = 0))
        val vm = newVm(repo)
        assertNotNull(vm.uiState.value.telemetry)
        assertEquals(ConnectionState.CONNECTED, vm.uiState.value.connectionEffective)
    }

    // ---------------------------------------------------------------- órdenes

    @Test
    fun laOrdenQuedaPendienteMientrasEsperaYLuegoSeConfirma() = runTest {
        val repo = FakeRobotRepository()
        val gate = CompletableDeferred<CommandResponse>()
        repo.responder = { gate.await() }
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = currentTime))
        assertTrue(vm.avail(CommandType.START_MISSION).enabled)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        runCurrent()
        val pending = vm.uiState.value.commands.single()
        assertEquals(CommandStatus.PENDING, pending.status)
        assertEquals("CMD-001", pending.command.commandId)
        assertEquals("operador-1", pending.command.operatorId)
        assertEquals(10, pending.command.expiresInSeconds)
        assertFalse(vm.avail(CommandType.START_MISSION).enabled)
        assertTrue(vm.avail(CommandType.START_MISSION).reason!!.contains("Esperando"))
        assertEquals(1, repo.sent.size)

        gate.complete(CommandResponse(pending.command.commandId, CommandStatus.CONFIRMED))
        runCurrent()
        val done = vm.uiState.value.commands.single()
        assertEquals(CommandStatus.CONFIRMED, done.status)
        assertNotNull(done.resolvedAt)
        assertTrue(vm.avail(CommandType.START_MISSION).enabled)
    }

    @Test
    fun rechazoYSinRespuestaMuestranSuEstadoYMotivo() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = currentTime))

        repo.responder = { CommandResponse(it.commandId, CommandStatus.REJECTED, "Batería baja") }
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        runCurrent()
        val rejected = vm.uiState.value.commands.first()
        assertEquals(CommandStatus.REJECTED, rejected.status)
        assertEquals("Batería baja", rejected.reason)

        repo.responder = { CommandResponse(it.commandId, CommandStatus.NO_RESPONSE, "Sin respuesta") }
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        runCurrent()
        assertEquals(CommandStatus.NO_RESPONSE, vm.uiState.value.commands.first().status)
        assertEquals(2, vm.uiState.value.commands.size)
    }

    @Test
    fun unaOrdenDeshabilitadaNoSeEnvia() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo) // sin telemetría
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertTrue(vm.uiState.value.commands.isEmpty())

        push(repo, testTelemetry(state = RobotState.ON_MISSION, timestamp = currentTime))
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION)) // ya en misión
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, Zone.HALL))
        runCurrent()
        assertTrue(repo.sent.isEmpty())
    }

    @Test
    fun cancelarPideConfirmacionYSoloSeEnviaAlConfirmar() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(state = RobotState.ON_MISSION, timestamp = currentTime))

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.CANCEL_MISSION))
        runCurrent()
        assertEquals(CommandType.CANCEL_MISSION, vm.uiState.value.confirmation)
        assertTrue(repo.sent.isEmpty())

        vm.onEvent(RobotUiEvent.DismissConfirmation)
        assertNull(vm.uiState.value.confirmation)
        vm.onEvent(RobotUiEvent.ConfirmPending) // sin confirmación pendiente: no hace nada
        runCurrent()
        assertTrue(repo.sent.isEmpty())

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.CANCEL_MISSION))
        vm.onEvent(RobotUiEvent.ConfirmPending)
        runCurrent()
        assertNull(vm.uiState.value.confirmation)
        assertEquals(listOf(CommandType.CANCEL_MISSION), repo.sent.map { it.type })
    }

    @Test
    fun laConfirmacionSeRevalidaAlConfirmar() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(state = RobotState.ON_MISSION, timestamp = currentTime))
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.CANCEL_MISSION))
        runCurrent()
        // Mientras el operador dudaba, la ronda terminó.
        push(repo, testTelemetry(state = RobotState.IDLE, timestamp = currentTime, sequence = 2))
        vm.onEvent(RobotUiEvent.ConfirmPending)
        runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertNull(vm.uiState.value.confirmation)
    }

    @Test
    fun laDetencionOperativaSinConexionQuedaSinRespuesta() = runTest {
        val repo = FakeRobotRepository()
        repo.responder = { CommandResponse(it.commandId, CommandStatus.NO_RESPONSE, "Sin respuesta") }
        val vm = newVm(repo) // sin datos: sin conexión
        assertTrue(vm.avail(CommandType.OPERATIONAL_STOP).enabled)
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.OPERATIONAL_STOP))
        assertEquals(CommandType.OPERATIONAL_STOP, vm.uiState.value.confirmation)
        vm.onEvent(RobotUiEvent.ConfirmPending)
        runCurrent()
        assertEquals(CommandStatus.NO_RESPONSE, vm.uiState.value.commands.single().status)
    }

    // ---------------------------------------------------------- datos antiguos

    @Test
    fun pasadosMasDe5SegundosLosDatosSeMarcanAntiguosYSeConservaLaUltimaHora() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = 777L))
        assertFalse(vm.uiState.value.isStale)

        advanceTimeBy(5_000)
        runCurrent()
        assertFalse("a 5000 ms exactos todavía no es antiguo", vm.uiState.value.isStale)

        advanceTimeBy(1)
        runCurrent()
        val s = vm.uiState.value
        assertTrue("a 5001 ms es antiguo", s.isStale)
        assertEquals(777L, s.lastUpdateMillis)
        assertEquals(ConnectionState.DISCONNECTED, s.connectionEffective)
        assertFalse(vm.avail(CommandType.START_MISSION).enabled)
        assertTrue(vm.avail(CommandType.START_MISSION).reason!!.contains("Sin conexión"))
        assertTrue(vm.avail(CommandType.OPERATIONAL_STOP).enabled)

        push(repo, testTelemetry(timestamp = currentTime, sequence = 2))
        assertFalse(vm.uiState.value.isStale)
        assertEquals(ConnectionState.CONNECTED, vm.uiState.value.connectionEffective)
        assertTrue(vm.avail(CommandType.START_MISSION).enabled)
    }

    @Test
    fun unPaqueteNuevoReiniciaLaCuentaDeLos5Segundos() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = 0))
        advanceTimeBy(4_000)
        push(repo, testTelemetry(timestamp = currentTime, sequence = 2))
        advanceTimeBy(4_000) // 8 s desde el primero, 4 s desde el último
        runCurrent()
        assertFalse(vm.uiState.value.isStale)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(vm.uiState.value.isStale)
    }

    // --------------------------------------------------------- CONNECTION_LOST

    @Test
    fun connectionLostBloqueaLasOrdenesDeInmediatoYSeCierraConElPrimerPaquetePosterior() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = 0))
        advanceTimeBy(2_000)
        runCurrent()

        pushEvent(repo, testEvent(type = EventType.CONNECTION_LOST, severity = Severity.CRITICAL))
        var s = vm.uiState.value
        assertFalse(s.isStale) // todavía no pasaron 5 s
        assertEquals(ConnectionState.DISCONNECTED, s.connectionEffective)
        assertFalse(vm.avail(CommandType.START_MISSION).enabled)
        assertTrue(vm.avail(CommandType.OPERATIONAL_STOP).enabled)
        assertEquals(1, s.activeAlerts.size)

        // Paquete que ya estaba en vuelo (generado antes de la pérdida): no cierra la alerta.
        push(repo, testTelemetry(timestamp = 1_999, sequence = 2))
        s = vm.uiState.value
        assertEquals(1, s.activeAlerts.size)
        assertFalse(vm.avail(CommandType.START_MISSION).enabled)

        advanceTimeBy(1_000)
        runCurrent()
        push(repo, testTelemetry(timestamp = currentTime, sequence = 3)) // reconectó
        s = vm.uiState.value
        assertTrue(s.activeAlerts.isEmpty())
        assertEquals(ConnectionState.CONNECTED, s.connectionEffective)
        assertTrue(vm.avail(CommandType.START_MISSION).enabled)
    }

    // ------------------------------------------------------ conexión recuperada

    @Test
    fun alArrancarNoSeMuestraConexionRecuperada() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        assertFalse(vm.uiState.value.connectionRecovered)
        push(repo, testTelemetry(timestamp = 0))
        assertFalse(vm.uiState.value.connectionRecovered)
    }

    @Test
    fun trasUnaPerdidaElPrimerPaquetePosteriorMarcaLaConexionRecuperada() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(state = RobotState.PAUSED, zone = Zone.HALL, timestamp = 0))
        advanceTimeBy(2_000)
        runCurrent()
        pushEvent(repo, testEvent(type = EventType.CONNECTION_LOST, severity = Severity.CRITICAL))
        assertFalse(vm.uiState.value.connectionRecovered)

        // Un paquete que ya estaba en vuelo no cuenta como reconexión.
        push(repo, testTelemetry(timestamp = 1_999, sequence = 2))
        assertFalse(vm.uiState.value.connectionRecovered)

        advanceTimeBy(1_000)
        runCurrent()
        push(repo, testTelemetry(state = RobotState.PAUSED, zone = Zone.HALL, timestamp = currentTime, sequence = 3))
        assertTrue(vm.uiState.value.connectionRecovered)
    }

    @Test
    fun tambienSeRecuperaDespuesDeDatosAntiguosSinEventoDePerdida() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = 0))
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(vm.uiState.value.isStale)
        assertFalse(vm.uiState.value.connectionRecovered)
        push(repo, testTelemetry(timestamp = currentTime, sequence = 2))
        assertTrue(vm.uiState.value.connectionRecovered)
        assertFalse(vm.uiState.value.isStale)
    }

    @Test
    fun elAvisoDeRecuperacionSeRetiraAlEnviarUnaOrdenOPasados10Segundos() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        fun recover(seq: Long) {
            push(repo, testTelemetry(timestamp = currentTime, sequence = seq)) // llega un paquete tras la pérdida
        }
        push(repo, testTelemetry(timestamp = 0))
        advanceTimeBy(5_001)
        runCurrent() // datos antiguos
        recover(2)
        assertTrue(vm.uiState.value.connectionRecovered)

        // Un paquete nuevo por segundo no reinicia la cuenta de los 10 s.
        repeat(9) {
            advanceTimeBy(1_000)
            push(repo, testTelemetry(timestamp = currentTime, sequence = 3L + it))
        }
        assertTrue("a los 9 s sigue visible", vm.uiState.value.connectionRecovered)
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse("a los 10 s se retira", vm.uiState.value.connectionRecovered)

        // Otra recuperación: se retira apenas el operador envía una orden.
        advanceTimeBy(5_001)
        runCurrent()
        recover(50)
        assertTrue(vm.uiState.value.connectionRecovered)
        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        runCurrent()
        assertFalse(vm.uiState.value.connectionRecovered)
    }

    @Test
    fun siSePierdeOtraVezElAvisoDeRecuperacionDesaparece() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(repo, testTelemetry(timestamp = 0))
        advanceTimeBy(5_001)
        runCurrent()
        push(repo, testTelemetry(timestamp = currentTime, sequence = 2))
        assertTrue(vm.uiState.value.connectionRecovered)
        pushEvent(repo, testEvent(type = EventType.CONNECTION_LOST, severity = Severity.CRITICAL))
        assertFalse(vm.uiState.value.connectionRecovered)
    }

    // ------------------------------------------------------------------ alertas

    @Test
    fun reconocerLaAlertaLaCierraYHabilitaReanudar() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        push(
            repo,
            testTelemetry(state = RobotState.PAUSED, zone = Zone.PERGOLA, activeAlerts = 1, timestamp = 0)
        )
        pushEvent(repo, testEvent(zone = Zone.PERGOLA))
        assertFalse(vm.avail(CommandType.RESUME_MISSION).enabled)
        assertTrue(vm.avail(CommandType.RESUME_MISSION).reason!!.contains("Reconocé"))
        assertTrue(vm.avail(CommandType.ACK_ALERT).enabled)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.ACK_ALERT))
        runCurrent()
        assertTrue(vm.uiState.value.activeAlerts.isEmpty())
        assertTrue(vm.avail(CommandType.RESUME_MISSION).enabled)
        assertFalse(vm.avail(CommandType.ACK_ALERT).enabled)
    }

    @Test
    fun laRevisionManualCierraSoloLasAlertasDeEsaZona() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        val statuses = Zone.entries.associateWith { ZoneStatus.FREE }.toMutableMap().apply {
            put(Zone.RESTROOM_DOOR, ZoneStatus.MANUAL_REVIEW)
            put(Zone.LAWN, ZoneStatus.NOT_VERIFIABLE)
        }
        push(repo, testTelemetry(activeAlerts = 2, statuses = statuses, timestamp = 0))
        pushEvent(
            repo,
            testEvent("E-1", EventType.ZONE_NOT_VERIFIABLE, Severity.INFO, Zone.RESTROOM_DOOR)
        )
        pushEvent(repo, testEvent("E-2", EventType.ZONE_NOT_VERIFIABLE, Severity.WARNING, Zone.LAWN))
        assertEquals(listOf(Zone.RESTROOM_DOOR, Zone.LAWN), vm.uiState.value.zonesNeedingReview)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.MANUAL_REVIEW, Zone.LAWN))
        runCurrent()
        val sent = repo.sent.single()
        assertEquals(Zone.LAWN, sent.zone)
        val open = vm.uiState.value.activeAlerts
        assertEquals(listOf("E-1"), open.map { it.event.eventId })
    }

    @Test
    fun siElRobotInformaCeroAlertasSeCierranLasQueYaConocia() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        advanceTimeBy(100)
        pushEvent(repo, testEvent("E-1")) // recibida en t=100

        // Mismo instante: el paquete pudo generarse antes del evento → no se cierra.
        push(repo, testTelemetry(activeAlerts = 0, timestamp = 100))
        assertEquals(1, vm.uiState.value.activeAlerts.size)

        advanceTimeBy(100)
        runCurrent()
        pushEvent(repo, testEvent("E-2")) // recibida en t=200
        // Paquete en vuelo generado en t=150, anterior a E-2: E-2 sigue abierta.
        push(repo, testTelemetry(activeAlerts = 0, timestamp = 150, sequence = 2))
        assertEquals(listOf("E-2"), vm.uiState.value.activeAlerts.map { it.event.eventId })

        // Un paquete posterior que sigue en 0 las cierra todas.
        push(repo, testTelemetry(activeAlerts = 0, timestamp = 300, sequence = 3))
        assertTrue(vm.uiState.value.activeAlerts.isEmpty())
    }

    @Test
    fun missionCompleteLlegaComoAlertaYaCerrada() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        pushEvent(
            repo,
            testEvent("E-9", EventType.MISSION_COMPLETE, Severity.INFO, acknowledged = true)
        )
        val alert = vm.uiState.value.alerts.single()
        assertTrue(alert.closed)
        assertTrue(vm.uiState.value.activeAlerts.isEmpty())
    }

    // ----------------------------------------------------------------- batería

    @Test
    fun nivelDeBateria() = runTest {
        val repo = FakeRobotRepository()
        val vm = newVm(repo)
        fun level(b: Int, seq: Long): BatteryLevel {
            push(repo, testTelemetry(battery = b, timestamp = currentTime, sequence = seq))
            return vm.uiState.value.batteryLevel
        }
        assertEquals(BatteryLevel.OK, level(21, 1))
        assertEquals(BatteryLevel.WARNING, level(20, 2))
        assertEquals(BatteryLevel.WARNING, level(10, 3))
        assertEquals(BatteryLevel.CRITICAL, level(9, 4))
    }

    // -------------------------------------------------------------- simulación

    private class RecordingSim : SimulationControls {
        val calls = mutableListOf<String>()
        override fun forceDisconnect() { calls += "disconnect" }
        override fun forceReconnect() { calls += "reconnect" }
        override fun rejectNextCommand() { calls += "reject" }
        override fun forceLowBattery() { calls += "battery" }
        override fun forcePersonDetected() { calls += "person" }
        override fun forceZoneNotVerifiable() { calls += "zone" }
    }

    @Test
    fun lasAccionesDeSimulacionSeDelegan() = runTest {
        val sim = RecordingSim()
        val vm = newVm(FakeRobotRepository(), sim)
        assertTrue(vm.uiState.value.simulationAvailable)
        SimulationAction.entries.forEach { vm.onEvent(RobotUiEvent.Simulate(it)) }
        assertEquals(
            listOf("disconnect", "reconnect", "reject", "battery", "person", "zone"),
            sim.calls
        )
    }

    @Test
    fun rechazarLaProximaOrdenMuestraUnAvisoBreveQueSeRetiraSolo() = runTest {
        val sim = RecordingSim()
        val vm = newVm(FakeRobotRepository(), sim)
        assertNull(vm.uiState.value.simulationNotice)

        vm.onEvent(RobotUiEvent.Simulate(SimulationAction.REJECT_NEXT_COMMAND))
        val first = vm.uiState.value.simulationNotice!!
        assertEquals("La próxima orden será rechazada.", first.text)

        advanceTimeBy(RobotViewModel.NOTICE_MILLIS - 1)
        runCurrent()
        assertEquals("sigue visible a los 3,999 s", first, vm.uiState.value.simulationNotice)
        advanceTimeBy(1)
        runCurrent()
        assertNull("se retira a los 4 s", vm.uiState.value.simulationNotice)
    }

    @Test
    fun unNuevoAvisoReiniciaLaCuentaYTieneOtroId() = runTest {
        val vm = newVm(FakeRobotRepository(), RecordingSim())
        vm.onEvent(RobotUiEvent.Simulate(SimulationAction.REJECT_NEXT_COMMAND))
        val first = vm.uiState.value.simulationNotice!!
        advanceTimeBy(3_000)
        runCurrent()
        vm.onEvent(RobotUiEvent.Simulate(SimulationAction.REJECT_NEXT_COMMAND))
        val second = vm.uiState.value.simulationNotice!!
        assertTrue(second.id > first.id)

        advanceTimeBy(3_000) // 6 s desde el primero, 3 s desde el segundo
        runCurrent()
        assertNotNull(vm.uiState.value.simulationNotice)
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(vm.uiState.value.simulationNotice)
    }

    @Test
    fun lasDemasAccionesDeSimulacionNoMuestranAviso() = runTest {
        val vm = newVm(FakeRobotRepository(), RecordingSim())
        SimulationAction.entries.filter { it != SimulationAction.REJECT_NEXT_COMMAND }.forEach {
            vm.onEvent(RobotUiEvent.Simulate(it))
            assertNull(vm.uiState.value.simulationNotice)
        }
    }

    @Test
    fun sinSimuladorNoHayAvisoAunqueSePidaRechazar() = runTest {
        val vm = newVm(FakeRobotRepository(), null)
        vm.onEvent(RobotUiEvent.Simulate(SimulationAction.REJECT_NEXT_COMMAND))
        assertNull(vm.uiState.value.simulationNotice)
    }

    @Test
    fun sinSimuladorLasAccionesSeIgnoran() = runTest {
        val vm = newVm(FakeRobotRepository(), null)
        assertFalse(vm.uiState.value.simulationAvailable)
        SimulationAction.entries.forEach { vm.onEvent(RobotUiEvent.Simulate(it)) } // no falla
    }

    // ------------------------------------------------------ integración con el simulador

    @Test
    fun escenarioPersonaDetectadaConElSimuladorReal() = runTest {
        val sim = SimulatedRobotRepository(backgroundScope, clock = { currentTime })
        val vm = RobotViewModel(sim, sim, clock = { currentTime })
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(RobotState.IDLE, vm.uiState.value.telemetry!!.state)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.START_MISSION))
        advanceTimeBy(1_100) // latencia de la primera orden: 1 s
        runCurrent()
        assertEquals(CommandStatus.CONFIRMED, vm.uiState.value.commands.first().status)

        var guard = 0
        while (vm.uiState.value.telemetry?.zone != Zone.PERGOLA) {
            check(guard++ < 200) { "No llegó al patio" }
            advanceTimeBy(1_000)
            runCurrent()
        }

        vm.onEvent(RobotUiEvent.Simulate(SimulationAction.PERSON_DETECTED))
        advanceTimeBy(1_500)
        runCurrent()
        var s = vm.uiState.value
        assertEquals(RobotState.PAUSED, s.telemetry!!.state)
        assertEquals(1, s.telemetry!!.peopleDetected)
        assertTrue(s.activeAlerts.any { it.event.type == EventType.PERSON_DETECTED })
        assertFalse(vm.avail(CommandType.RESUME_MISSION).enabled)
        assertTrue(vm.avail(CommandType.ACK_ALERT).enabled)
        assertTrue(vm.avail(CommandType.RETURN_TO_SAFE_POINT).enabled) // el operador puede volver

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.ACK_ALERT))
        advanceTimeBy(1_400) // latencia de la segunda orden: 1,25 s
        runCurrent()
        s = vm.uiState.value
        assertEquals(CommandStatus.CONFIRMED, s.commands.first().status)
        assertTrue(s.activeAlerts.isEmpty())
        assertEquals(ZoneStatus.PERSON_FOUND, s.telemetry!!.zonesStatus[Zone.PERGOLA])
        assertTrue(vm.avail(CommandType.RESUME_MISSION).enabled)

        vm.onEvent(RobotUiEvent.RequestCommand(CommandType.RESUME_MISSION))
        advanceTimeBy(2_700) // latencia de la tercera orden: 1,5 s
        runCurrent()
        assertEquals(RobotState.ON_MISSION, vm.uiState.value.telemetry!!.state)
        assertEquals(CommandStatus.CONFIRMED, vm.uiState.value.commands.first().status)
    }
}
