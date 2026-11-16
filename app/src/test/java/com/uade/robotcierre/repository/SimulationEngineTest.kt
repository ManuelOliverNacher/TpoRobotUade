package com.uade.robotcierre.repository

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arnés: reloj falso (1 s por tick), registro de telemetría y eventos. */
private class Sim(config: SimulationConfig = SimulationConfig()) {
    var now = 0L
    val events = mutableListOf<RobotEvent>()
    val log = mutableListOf<Telemetry>()
    val engine = SimulationEngine(config, { now }, { events += it })
    private var commandNumber = 0

    /** Avanza [times] segundos y devuelve el resultado del último tick (null = sin conexión). */
    fun tick(times: Int = 1): Telemetry? {
        var last: Telemetry? = null
        repeat(times) {
            now += 1_000
            last = engine.tick()
            last?.let { log += it }
        }
        return last
    }

    fun send(type: CommandType, zone: Zone? = null): CommandResponse =
        engine.handle(Command("C-${commandNumber++}", type, now, "op-1", 10, zone))

    fun runUntilIdle(limit: Int = 300): Int {
        var ticks = 0
        while (ticks < limit) {
            ticks++
            if (tick()!!.state == RobotState.IDLE) return ticks
        }
        error("La ronda no terminó en $limit ticks")
    }

    fun events(type: EventType) = events.filter { it.type == type }
}

class SimulationEngineTest {

    @Test
    fun telemetriaInicialEnReposo() {
        val sim = Sim()
        val t = sim.tick()!!
        assertEquals(RobotState.IDLE, t.state)
        assertEquals(1L, t.sequence)
        assertEquals(100, t.batteryPercent)
        assertEquals(0, t.checkpoint)
        assertNull(t.missionId)
        assertNull(t.zone)
        assertFalse(t.moving)
        assertTrue(t.zonesStatus.values.all { it == ZoneStatus.PENDING })
        assertEquals(LocalizationQuality.GOOD, t.localizationQuality)
    }

    @Test
    fun misionNormalRecorreLas6ZonasEnOrdenYVuelveALaBase() {
        val sim = Sim()
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.START_MISSION).status)
        val ticks = sim.runUntilIdle()

        val visited = sim.log.filter { it.state == RobotState.ON_MISSION }
            .mapNotNull { it.zone }.distinct()
        assertEquals(Zone.entries.toList(), visited)
        assertTrue(sim.log.any { it.state == RobotState.RETURNING })
        assertTrue("ticks=$ticks", ticks in 130..140)

        val last = sim.log.last()
        assertEquals(RobotState.IDLE, last.state)
        assertNull(last.missionId)
        // 5 zonas libres; el baño SIEMPRE queda en revisión manual.
        assertEquals(ZoneStatus.MANUAL_REVIEW, last.zonesStatus[Zone.RESTROOM_DOOR])
        assertEquals(5, last.zonesVerified)
        assertEquals(1, sim.events(EventType.MISSION_COMPLETE).size)
        // Una ronda completa gasta ~30–35 % de batería y no dispara el aviso del 20 %.
        assertTrue("batería=${last.batteryPercent}", 100 - last.batteryPercent in 30..35)
        assertTrue(sim.events(EventType.LOW_BATTERY).isEmpty())
    }

    @Test
    fun bateriaBajaAvisaUnaSolaVezConLasZonasQueFaltan() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        sim.tick(45) // en el baño (zona 3), Hall y Pasillo ya libres
        sim.engine.forceLowBattery()
        sim.tick(4)

        val warnings = sim.events(EventType.LOW_BATTERY)
        assertEquals(1, warnings.size)
        assertEquals(Severity.WARNING, warnings[0].severity)
        assertTrue(warnings[0].details.contains("Puerta del baño"))
        assertTrue(warnings[0].details.contains("Explanada y rampa"))
        assertFalse(warnings[0].details.contains("Hall"))
        assertFalse(warnings[0].details.contains("Pasillo"))

        sim.tick(60)
        assertEquals(1, sim.events(EventType.LOW_BATTERY).size)
    }

    @Test
    fun noSePuedeIniciarConBateriaMenorA10YSiConExactamente10() {
        val low = Sim(SimulationConfig(initialBatteryPercent = 5.0))
        val r = low.send(CommandType.START_MISSION)
        assertEquals(CommandStatus.REJECTED, r.status)
        assertTrue(r.reason!!.contains("Batería"))

        val edge = Sim(SimulationConfig(initialBatteryPercent = 10.0))
        assertEquals(CommandStatus.CONFIRMED, edge.send(CommandType.START_MISSION).status)
    }

    @Test
    fun noSePuedeIniciarUnaRondaYaEnCurso() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        val r = sim.send(CommandType.START_MISSION)
        assertEquals(CommandStatus.REJECTED, r.status)
        assertEquals("Ya hay una ronda en curso.", r.reason)
    }

    @Test
    fun desconexionPausaLaRondaCortaLaTelemetriaYExigeReanudar() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        val before = sim.tick(10)!! // 10 s de traslado hacia el Hall

        sim.engine.forceDisconnect()
        sim.engine.forceDisconnect() // idempotente
        assertFalse(sim.engine.isConnected)
        repeat(5) { assertNull(sim.tick()) }
        val lost = sim.events(EventType.CONNECTION_LOST)
        assertEquals(1, lost.size)
        assertEquals(Severity.CRITICAL, lost[0].severity)

        sim.engine.forceReconnect()
        val back = sim.tick()!!
        assertEquals(before.sequence + 1, back.sequence) // la secuencia sigue
        assertEquals(RobotState.PAUSED, back.state) // espera RESUME del operador
        assertEquals(Zone.HALL, back.zone)
        assertFalse(back.moving)
        assertEquals(0, back.activeAlerts) // CONNECTION_LOST se cierra al reconectar

        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.RESUME_MISSION).status)
        // El avance estuvo congelado: faltan 5 s de traslado desde el segundo 10 original.
        repeat(4) { assertTrue(sim.tick()!!.moving) }
        val verifying = sim.tick()!!
        assertEquals(RobotState.ON_MISSION, verifying.state)
        assertFalse(verifying.moving) // ya está verificando
    }

    @Test
    fun rejectNextCommandSoloAfectaAlSiguiente() {
        val sim = Sim()
        sim.engine.rejectNextCommand()
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.START_MISSION).status)
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.START_MISSION).status)
    }

    @Test
    fun personaDetectadaDetieneAlRobotYAlReconocerLaZonaSigueEnPersonFound() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        assertEquals(Zone.PERGOLA, sim.tick(65)!!.zone)

        sim.engine.forcePersonDetected()
        val stopped = sim.tick()!!
        assertEquals(RobotState.PAUSED, stopped.state)
        assertFalse(stopped.moving)
        assertEquals(1, stopped.peopleDetected)
        assertEquals(2, stopped.activeAlerts) // persona + revisión manual pendiente del baño
        assertEquals(ZoneStatus.PERSON_FOUND, stopped.zonesStatus[Zone.PERGOLA])
        val event = sim.events(EventType.PERSON_DETECTED).single()
        assertEquals(Severity.CRITICAL, event.severity)
        assertEquals(Zone.PERGOLA, event.zone)

        // Sigue detenido y no se puede reanudar sin reconocer la alerta.
        assertEquals(Zone.PERGOLA, sim.tick(30)!!.zone)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.RESUME_MISSION).status)

        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.ACK_ALERT).status)
        val acked = sim.tick()!!
        assertEquals(0, acked.activeAlerts)
        assertEquals(ZoneStatus.PERSON_FOUND, acked.zonesStatus[Zone.PERGOLA]) // NO pasa a FREE

        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.RESUME_MISSION).status)
        sim.runUntilIdle()
        val last = sim.log.last()
        assertEquals(ZoneStatus.PERSON_FOUND, last.zonesStatus[Zone.PERGOLA])
        assertEquals(4, last.zonesVerified) // Hall, Pasillo, Césped, Explanada
    }

    @Test
    fun trasPersonaElOperadorPuedeVolverALaBase() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        sim.tick(65)
        sim.engine.forcePersonDetected()
        sim.tick()
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.RETURN_TO_SAFE_POINT).status)
        assertEquals(RobotState.RETURNING, sim.tick()!!.state)
        sim.runUntilIdle()
        assertEquals(RobotState.IDLE, sim.log.last().state)
        assertTrue(sim.events(EventType.MISSION_COMPLETE).isEmpty()) // no completó la ruta
    }

    @Test
    fun zonaNoVerificableDegradaLaLocalizacionYVuelveAGoodAlPasarALaZonaSiguiente() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        assertEquals(Zone.LAWN, sim.tick(85)!!.zone)

        sim.engine.forceZoneNotVerifiable()
        val degraded = sim.tick()!!
        assertEquals(LocalizationQuality.DEGRADED, degraded.localizationQuality)
        assertEquals(ZoneStatus.NOT_VERIFIABLE, degraded.zonesStatus[Zone.LAWN])
        val event = sim.events(EventType.ZONE_NOT_VERIFIABLE).single { it.zone == Zone.LAWN }
        assertEquals(Severity.WARNING, event.severity)
        assertEquals(Zone.LAWN, event.zone)

        val next = sim.tick(15)!!
        assertEquals(Zone.ESPLANADE, next.zone)
        assertEquals(LocalizationQuality.GOOD, next.localizationQuality)
        assertEquals(ZoneStatus.NOT_VERIFIABLE, next.zonesStatus[Zone.LAWN]) // sigue pendiente

        assertEquals(
            CommandStatus.CONFIRMED,
            sim.send(CommandType.MANUAL_REVIEW, Zone.LAWN).status
        )
        assertEquals(ZoneStatus.VERIFIED_MANUALLY, sim.tick()!!.zonesStatus[Zone.LAWN])
    }

    @Test
    fun manualReviewDeLaZonaDegradadaRestauraLaLocalizacion() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        sim.tick(85)
        sim.engine.forceZoneNotVerifiable()
        sim.tick()
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.MANUAL_REVIEW, Zone.LAWN).status)
        val t = sim.tick()!!
        assertEquals(Zone.LAWN, t.zone) // todavía en la misma zona
        assertEquals(LocalizationQuality.GOOD, t.localizationQuality)
        assertEquals(ZoneStatus.VERIFIED_MANUALLY, t.zonesStatus[Zone.LAWN])
        assertEquals(1, t.activeAlerts) // cierra la alerta del césped; queda la del baño
    }

    @Test
    fun elBanoNuncaSePuedeForzarNoVerificableYSiempreRequiereRevisionManual() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        assertEquals(Zone.RESTROOM_DOOR, sim.tick(45)!!.zone)
        sim.engine.forceZoneNotVerifiable()
        assertTrue(sim.events(EventType.ZONE_NOT_VERIFIABLE).isEmpty())

        sim.runUntilIdle()
        assertEquals(ZoneStatus.MANUAL_REVIEW, sim.log.last().zonesStatus[Zone.RESTROOM_DOOR])
        assertEquals(1, sim.log.last().activeAlerts)

        assertEquals(
            CommandStatus.CONFIRMED,
            sim.send(CommandType.MANUAL_REVIEW, Zone.RESTROOM_DOOR).status
        )
        val t = sim.tick()!!
        assertEquals(ZoneStatus.VERIFIED_MANUALLY, t.zonesStatus[Zone.RESTROOM_DOOR])
        assertEquals(6, t.zonesVerified)
        assertEquals(0, t.activeAlerts)
        // Segunda revisión y zona ya libre: rechazadas.
        assertEquals(
            CommandStatus.REJECTED,
            sim.send(CommandType.MANUAL_REVIEW, Zone.RESTROOM_DOOR).status
        )
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.MANUAL_REVIEW, Zone.HALL).status)
    }

    @Test
    fun reglasDeRechazoDelRobot() {
        val sim = Sim()
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.PAUSE_MISSION).status)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.RESUME_MISSION).status)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.CANCEL_MISSION).status)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.ACK_ALERT).status)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.RETURN_TO_SAFE_POINT).status)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.MANUAL_REVIEW).status) // sin zona
        assertEquals(
            CommandStatus.REJECTED,
            sim.send(CommandType.MANUAL_REVIEW, Zone.HALL).status // todavía no visitada
        )
    }

    @Test
    fun pausarReanudarYCancelar() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        sim.tick(5)
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.PAUSE_MISSION).status)
        assertEquals(RobotState.PAUSED, sim.tick()!!.state)
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.RESUME_MISSION).status)
        assertEquals(RobotState.ON_MISSION, sim.tick()!!.state)

        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.CANCEL_MISSION).status)
        assertEquals(RobotState.RETURNING, sim.tick()!!.state)
        sim.runUntilIdle()
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.CANCEL_MISSION).status)
    }

    @Test
    fun detencionOperativaYRegresoDesdeDetenido() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        sim.tick(10)
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.OPERATIONAL_STOP).status)
        val stopped = sim.tick()!!
        assertEquals(RobotState.STOPPED, stopped.state)
        assertFalse(stopped.moving)
        assertEquals(RobotState.STOPPED, sim.tick(20)!!.state) // no se reanuda solo
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.RESUME_MISSION).status)
        assertEquals(CommandStatus.REJECTED, sim.send(CommandType.START_MISSION).status)

        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.RETURN_TO_SAFE_POINT).status)
        sim.runUntilIdle()
        assertEquals(RobotState.IDLE, sim.log.last().state)
    }

    @Test
    fun rutaBloqueadaRechazaElRegreso() {
        val sim = Sim()
        sim.send(CommandType.START_MISSION)
        sim.tick(5)
        sim.engine.setRouteBlocked(true)
        val r = sim.send(CommandType.RETURN_TO_SAFE_POINT)
        assertEquals(CommandStatus.REJECTED, r.status)
        assertTrue(r.reason!!.contains("Ruta bloqueada"))
        sim.engine.setRouteBlocked(false)
        assertEquals(CommandStatus.CONFIRMED, sim.send(CommandType.RETURN_TO_SAFE_POINT).status)
    }

    @Test
    fun laSimulacionEsDeterminista() {
        fun script(): List<Telemetry> {
            val sim = Sim()
            sim.send(CommandType.START_MISSION)
            sim.tick(65)
            sim.engine.forcePersonDetected()
            sim.tick(3)
            sim.send(CommandType.ACK_ALERT)
            sim.send(CommandType.RESUME_MISSION)
            sim.tick(30)
            sim.engine.forceZoneNotVerifiable()
            sim.engine.forceLowBattery()
            sim.runUntilIdle()
            return sim.log
        }
        val a = script()
        val b = script()
        assertNotNull(a.firstOrNull())
        assertEquals(a, b)
    }
}
