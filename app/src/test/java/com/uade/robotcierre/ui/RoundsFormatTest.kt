package com.uade.robotcierre.ui

import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.components.Tone
import com.uade.robotcierre.ui.screens.roundSummary
import com.uade.robotcierre.viewmodel.testTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundsFormatTest {

    private fun statuses(vararg overrides: Pair<Zone, ZoneStatus>, base: ZoneStatus = ZoneStatus.FREE) =
        Zone.entries.associateWith { base }.toMutableMap().apply { putAll(overrides) }

    private val closed = statuses(Zone.RESTROOM_DOOR to ZoneStatus.MANUAL_REVIEW)

    @Test
    fun rondaCompletaConElBanoPendienteDeRevision() {
        val s = roundSummary(testTelemetry(statuses = closed), MissionResult.COMPLETED)!!
        assertEquals("Ronda completa.", s.title)
        assertEquals("Zonas libres: 5 de 6.", s.lines[0])
        assertTrue(s.lines.contains("Puerta del baño: requiere revisión manual."))
        assertEquals(listOf(Zone.RESTROOM_DOOR), s.reviewZones)
        assertEquals(Tone.WARNING, s.tone) // falta la revisión manual del baño
    }

    @Test
    fun conLaRevisionHechaYaNoHayNadaPendiente() {
        val ok = statuses(Zone.RESTROOM_DOOR to ZoneStatus.VERIFIED_MANUALLY)
        val s = roundSummary(testTelemetry(statuses = ok), MissionResult.COMPLETED)!!
        assertEquals("Zonas libres: 6 de 6.", s.lines[0])
        assertTrue(s.reviewZones.isEmpty())
        assertEquals(1, s.lines.size)
        assertEquals(Tone.OK, s.tone) // ya no queda nada por hacer
    }

    @Test
    fun rondaIncompletaListaLasZonasPendientes() {
        val partial = statuses(Zone.HALL to ZoneStatus.FREE, base = ZoneStatus.PENDING)
        val s = roundSummary(testTelemetry(statuses = partial), MissionResult.RETURNED_EARLY)!!
        assertTrue(s.title.contains("incompleta"))
        assertEquals("Zonas libres: 1 de 6.", s.lines[0])
        assertTrue(
            s.lines.contains(
                "Zonas pendientes: Pasillo de servicios, Puerta del baño, Zona techada del patio, " +
                    "Césped con asientos, Explanada y rampa."
            )
        )
        assertEquals(Tone.WARNING, s.tone)
        assertTrue(s.reviewZones.isEmpty())
    }

    @Test
    fun conPersonaEncontradaElResumenAvisaYQuedaEnAdvertencia() {
        val withPerson = statuses(
            Zone.RESTROOM_DOOR to ZoneStatus.MANUAL_REVIEW,
            Zone.PERGOLA to ZoneStatus.PERSON_FOUND
        )
        val s = roundSummary(testTelemetry(statuses = withPerson), MissionResult.COMPLETED)!!
        assertEquals("Zonas libres: 4 de 6.", s.lines[0])
        assertTrue(s.lines.contains("Zona techada del patio: se encontró una persona."))
        assertEquals(Tone.WARNING, s.tone)
    }

    @Test
    fun laRondaCanceladaTieneSuPropioTitulo() {
        val partial = statuses(Zone.HALL to ZoneStatus.FREE, base = ZoneStatus.PENDING)
        assertEquals("Ronda cancelada.", roundSummary(testTelemetry(statuses = partial), MissionResult.CANCELLED)!!.title)
        assertEquals("Ronda interrumpida.", roundSummary(testTelemetry(statuses = partial), MissionResult.INTERRUPTED)!!.title)
    }

    @Test
    fun sinResumenSiHayRondaEnCursoONoSeRecorrioNada() {
        val running = testTelemetry(state = RobotState.ON_MISSION, zone = Zone.HALL, statuses = closed)
        assertNull(roundSummary(running, MissionResult.COMPLETED))
        assertNull(roundSummary(testTelemetry(statuses = closed), MissionResult.IN_PROGRESS))
        assertNull(roundSummary(testTelemetry(statuses = closed), null))
        assertNull(roundSummary(null, MissionResult.COMPLETED))
        // Robot recién arrancado: todas las zonas pendientes, aunque el historial tenga rondas viejas.
        assertNull(roundSummary(testTelemetry(statuses = statuses(base = ZoneStatus.PENDING)), MissionResult.COMPLETED))
        assertNotNull(roundSummary(testTelemetry(statuses = closed), MissionResult.COMPLETED))
    }
}
