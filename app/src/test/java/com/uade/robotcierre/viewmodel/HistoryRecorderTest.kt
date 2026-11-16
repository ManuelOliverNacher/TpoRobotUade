package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRecorderTest {

    private val pending = Zone.entries.associateWith { ZoneStatus.PENDING }
    private fun done(restroom: ZoneStatus = ZoneStatus.MANUAL_REVIEW) =
        Zone.entries.associateWith { if (it == Zone.RESTROOM_DOOR) restroom else ZoneStatus.FREE }

    private class Env(val fake: FakeHistoryRepository, val recorder: HistoryRecorder)

    private fun TestScope.env(
        fake: FakeHistoryRepository = FakeHistoryRepository(),
        errors: MutableList<Throwable> = mutableListOf()
    ): Env {
        val recorder = HistoryRecorder(fake, backgroundScope, { currentTime }, { errors += it })
        runCurrent()
        return Env(fake, recorder)
    }

    private fun TestScope.mission(e: Env, code: String = "M-001", statuses: Map<Zone, ZoneStatus> = pending) {
        e.recorder.onTelemetry(
            testTelemetry(state = RobotState.ON_MISSION, missionId = code, zone = Zone.HALL, statuses = statuses)
        )
        runCurrent()
    }

    private fun TestScope.arrive(e: Env, statuses: Map<Zone, ZoneStatus>) {
        e.recorder.onTelemetry(testTelemetry(state = RobotState.IDLE, statuses = statuses))
        runCurrent()
    }

    @Test
    fun rondaCompletaSeAbreEnCursoYSeCierraComoCompleted() = runTest {
        val e = env()
        arrive(e, pending)
        assertTrue(e.fake.missionsNow.isEmpty()) // en reposo no hay ronda

        advanceTimeBy(1_000)
        mission(e)
        val open = e.fake.missionsNow.single()
        assertEquals(MissionResult.IN_PROGRESS, open.result)
        assertEquals("M-001", open.code)
        assertEquals(1_000L, open.startedAt)
        assertNull(open.endedAt)

        advanceTimeBy(115_000)
        arrive(e, done())
        val closed = e.fake.missionsNow.single()
        assertEquals(MissionResult.COMPLETED, closed.result)
        assertEquals(currentTime, closed.endedAt)
        assertEquals(5, closed.zonesVerified) // el baño queda en revisión manual
    }

    @Test
    fun cancelarYVolverAntesDeTiempoDejanSuResultado() = runTest {
        val e = env()
        mission(e)
        e.recorder.onCommandConfirmed(CommandType.CANCEL_MISSION)
        arrive(e, pending)
        assertEquals(MissionResult.CANCELLED, e.fake.missionsNow.single().result)

        mission(e, "M-002")
        e.recorder.onCommandConfirmed(CommandType.RETURN_TO_SAFE_POINT)
        arrive(e, pending)
        assertEquals(MissionResult.RETURNED_EARLY, e.fake.missionsNow.first().result)

        mission(e, "M-003") // volvió sin que el operador ordenara nada reconocible
        arrive(e, pending)
        assertEquals(MissionResult.RETURNED_EARLY, e.fake.missionsNow.first().result)
    }

    @Test
    fun missionCompleteMarcaCompletedAunqueFaltenZonas() = runTest {
        val e = env()
        mission(e)
        e.recorder.onEvent(testEvent("E-1", EventType.MISSION_COMPLETE, Severity.INFO, acknowledged = true))
        arrive(e, pending)
        assertEquals(MissionResult.COMPLETED, e.fake.missionsNow.single().result)
    }

    @Test
    fun unaOrdenConfirmadaSinRondaNoContaminaLaSiguiente() = runTest {
        val e = env()
        e.recorder.onCommandConfirmed(CommandType.CANCEL_MISSION) // sin ronda abierta
        mission(e)
        arrive(e, pending)
        assertEquals(MissionResult.RETURNED_EARLY, e.fake.missionsNow.single().result)
    }

    @Test
    fun losEventosSeGuardanConSuRondaOSinRondaYSeMarcanReconocidos() = runTest {
        val e = env()
        e.recorder.onEvent(testEvent("E-1", EventType.CONNECTION_LOST, Severity.CRITICAL))
        runCurrent()
        assertNull(e.fake.eventsNow.single().missionId) // fuera de una ronda

        mission(e)
        val missionId = e.fake.missionsNow.single().id
        e.recorder.onEvent(testEvent("E-2", EventType.PERSON_DETECTED, Severity.CRITICAL, Zone.PERGOLA))
        e.recorder.onEvent(testEvent("E-3", EventType.MISSION_COMPLETE, Severity.INFO, acknowledged = true))
        runCurrent()
        val byType = e.fake.eventsNow.associateBy { it.type }
        assertEquals(missionId, byType.getValue(EventType.PERSON_DETECTED).missionId)
        assertEquals(Zone.PERGOLA, byType.getValue(EventType.PERSON_DETECTED).zone)
        assertFalse(byType.getValue(EventType.PERSON_DETECTED).acknowledged)
        assertTrue(byType.getValue(EventType.MISSION_COMPLETE).acknowledged)

        e.recorder.onAlertClosed("E-2")
        e.recorder.onAlertClosed("E-999") // evento desconocido: se ignora
        runCurrent()
        assertTrue(e.fake.eventsNow.first { it.type == EventType.PERSON_DETECTED }.acknowledged)
        assertFalse(e.fake.eventsNow.first { it.type == EventType.CONNECTION_LOST }.acknowledged)
    }

    @Test
    fun elAvisoDeFinDeRondaQueLlegaDespuesDelCierreSeAsociaALaRondaCerrada() = runTest {
        val e = env()
        mission(e)
        arrive(e, done()) // el último paquete cierra la ronda antes de que llegue el aviso
        e.recorder.onEvent(testEvent("E-9", EventType.MISSION_COMPLETE, Severity.INFO, acknowledged = true))
        runCurrent()
        assertEquals(e.fake.missionsNow.single().id, e.fake.eventsNow.single().missionId)

        // Cualquier otro evento posterior al cierre sigue siendo "fuera de una ronda".
        e.recorder.onEvent(testEvent("E-10", EventType.CONNECTION_LOST, Severity.CRITICAL))
        runCurrent()
        assertNull(e.fake.eventsNow.last().missionId)
    }

    @Test
    fun laRevisionManualPosteriorSumaLaZonaALaRondaCerradaYNoALaSiguiente() = runTest {
        val e = env()
        mission(e)
        arrive(e, done())
        assertEquals(5, e.fake.missionsNow.single().zonesVerified)

        arrive(e, done(ZoneStatus.VERIFIED_MANUALLY)) // el operador revisó el baño
        assertEquals(6, e.fake.missionsNow.single().zonesVerified)

        mission(e, "M-002") // nueva ronda: zonas en PENDING
        assertEquals(6, e.fake.missionsNow.first { it.code == "M-001" }.zonesVerified)
        assertEquals(0, e.fake.missionsNow.first { it.code == "M-002" }.zonesVerified)
    }

    @Test
    fun laCantidadDeZonasVerificadasSeActualizaMientrasLaRondaEstaEnCurso() = runTest {
        val e = env()
        mission(e)
        val partial = pending.toMutableMap().apply {
            put(Zone.HALL, ZoneStatus.FREE)
            put(Zone.CORRIDOR, ZoneStatus.FREE)
        }
        mission(e, statuses = partial)
        assertEquals(2, e.fake.missionsNow.single().zonesVerified)
        assertEquals(MissionResult.IN_PROGRESS, e.fake.missionsNow.single().result)
    }

    @Test
    fun alArrancarSeCierranComoInterrumpidasLasRondasQueQuedaronAbiertas() = runTest {
        val fake = FakeHistoryRepository()
        fake.seed(MissionRecord(7, "M-001", 100, null, MissionResult.IN_PROGRESS, 3))
        fake.seed(MissionRecord(8, "M-002", 200, 300, MissionResult.COMPLETED, 5))
        advanceTimeBy(5_000)
        env(fake)
        val byId = fake.missionsNow.associateBy { it.id }
        assertEquals(MissionResult.INTERRUPTED, byId.getValue(7).result)
        assertEquals(5_000L, byId.getValue(7).endedAt)
        assertEquals(MissionResult.COMPLETED, byId.getValue(8).result)
    }

    @Test
    fun lasEscriturasSeHacenEnOrdenAunqueLaInsercionSeaLenta() = runTest {
        val fake = FakeHistoryRepository().apply { insertDelayMs = 50 }
        val e = env(fake)
        e.recorder.onEvent(testEvent("E-1", EventType.PERSON_DETECTED, Severity.CRITICAL))
        e.recorder.onAlertClosed("E-1") // llega antes de que termine la inserción
        advanceTimeBy(200)
        runCurrent()
        assertTrue(fake.eventsNow.single().acknowledged)
    }

    @Test
    fun siUnaEscrituraFallaSeAvisaYSeSigueGrabando() = runTest {
        val errors = mutableListOf<Throwable>()
        val e = env(errors = errors)
        e.fake.failNext = true
        e.recorder.onEvent(testEvent("E-1", EventType.OBSTACLE, Severity.WARNING))
        e.recorder.onEvent(testEvent("E-2", EventType.LOW_BATTERY, Severity.WARNING))
        runCurrent()
        assertEquals(1, errors.size)
        assertEquals(listOf(EventType.LOW_BATTERY), e.fake.eventsNow.map { it.type })
    }
}
