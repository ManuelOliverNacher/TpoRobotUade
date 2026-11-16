package com.uade.robotcierre.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba el historial contra una base Room real (hay que correrlo en emulador o dispositivo). */
@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomHistoryRepository

    private fun event(type: EventType, zone: Zone? = null, ack: Boolean = false) =
        RobotEvent("E-001", type, Severity.WARNING, zone, "detalle", ack)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = RoomHistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun rondaSeAbreSeActualizaYSeCierra() = runBlocking {
        val id = repo.openMission("M-001", startedAt = 1_000)
        var m = repo.missions.first().single()
        assertEquals(MissionResult.IN_PROGRESS, m.result)
        assertNull(m.endedAt)

        repo.updateZonesVerified(id, 3)
        repo.closeMission(id, endedAt = 9_000, result = MissionResult.COMPLETED, zonesVerified = 5)
        m = repo.missions.first().single()
        assertEquals("M-001", m.code)
        assertEquals(9_000L, m.endedAt)
        assertEquals(MissionResult.COMPLETED, m.result)
        assertEquals(5, m.zonesVerified)
    }

    @Test
    fun codigosRepetidosEntreSesionesNoChocan() = runBlocking {
        val a = repo.openMission("M-001", 1_000)
        val b = repo.openMission("M-001", 2_000) // el simulador reinicia el contador
        assertTrue(a != b)
        assertEquals(listOf(b, a), repo.missions.first().map { it.id }) // más nueva primero
    }

    @Test
    fun eventosPorRondaEnOrdenCronologicoYFueraDeRonda() = runBlocking {
        val m = repo.openMission("M-001", 1_000)
        repo.insertEvent(m, event(EventType.LOW_BATTERY), timestamp = 3_000)
        repo.insertEvent(m, event(EventType.PERSON_DETECTED, Zone.PERGOLA), timestamp = 2_000)
        repo.insertEvent(null, event(EventType.CONNECTION_LOST), timestamp = 500)
        repo.insertEvent(null, event(EventType.OBSTACLE), timestamp = 800)

        assertEquals(
            listOf(EventType.PERSON_DETECTED, EventType.LOW_BATTERY),
            repo.eventsOf(m).first().map { it.type }
        )
        assertEquals(Zone.PERGOLA, repo.eventsOf(m).first().first().zone)
        assertEquals(
            listOf(EventType.OBSTACLE, EventType.CONNECTION_LOST),
            repo.eventsOutsideMissions.first().map { it.type }
        )
    }

    @Test
    fun eventoSeMarcaReconocido() = runBlocking {
        val m = repo.openMission("M-001", 1_000)
        val e = repo.insertEvent(m, event(EventType.PERSON_DETECTED), 2_000)
        assertEquals(false, repo.eventsOf(m).first().single().acknowledged)
        repo.markEventAcknowledged(e)
        assertEquals(true, repo.eventsOf(m).first().single().acknowledged)
        // Un evento que ya llega reconocido se guarda así.
        repo.insertEvent(m, event(EventType.MISSION_COMPLETE, ack = true), 3_000)
        assertEquals(true, repo.eventsOf(m).first().last().acknowledged)
    }

    @Test
    fun cerrarInterrumpidasSoloAfectaALasEnCurso() = runBlocking {
        val open = repo.openMission("M-001", 1_000)
        val done = repo.openMission("M-002", 2_000)
        repo.closeMission(done, 3_000, MissionResult.COMPLETED, 5)

        repo.closeInterruptedMissions(now = 10_000)
        val byId = repo.missions.first().associateBy { it.id }
        assertEquals(MissionResult.INTERRUPTED, byId.getValue(open).result)
        assertEquals(10_000L, byId.getValue(open).endedAt)
        assertEquals(MissionResult.COMPLETED, byId.getValue(done).result)
        assertEquals(3_000L, byId.getValue(done).endedAt)
    }

    @Test
    fun alBorrarUnaRondaSeBorranSusEventos() = runBlocking {
        val m = repo.openMission("M-001", 1_000)
        repo.insertEvent(m, event(EventType.LOW_BATTERY), 2_000)
        repo.insertEvent(null, event(EventType.CONNECTION_LOST), 500)
        db.openHelper.writableDatabase.execSQL("DELETE FROM missions WHERE id = $m")
        assertTrue(repo.eventsOf(m).first().isEmpty())
        assertEquals(1, repo.eventsOutsideMissions.first().size)
    }

    @Test
    fun elHistorialSobreviveAlCierreDeLaBase() = runBlocking {
        val name = "history_survival_test.db"
        context.deleteDatabase(name)
        try {
            var file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            var r = RoomHistoryRepository(file.historyDao())
            val m = r.openMission("M-001", 1_000)
            r.insertEvent(m, event(EventType.LOW_BATTERY), 2_000)
            r.closeMission(m, 5_000, MissionResult.CANCELLED, 2)
            file.close() // como si se cerrara la app

            file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            r = RoomHistoryRepository(file.historyDao())
            val mission = r.missions.first().single()
            assertEquals(MissionResult.CANCELLED, mission.result)
            assertEquals(2, mission.zonesVerified)
            assertEquals(EventType.LOW_BATTERY, r.eventsOf(mission.id).first().single().type)
            file.close()
        } finally {
            context.deleteDatabase(name)
        }
    }
}
