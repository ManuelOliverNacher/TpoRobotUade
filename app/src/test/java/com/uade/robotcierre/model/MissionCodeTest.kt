package com.uade.robotcierre.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissionCodeTest {

    @Test
    fun formatoYAnalisisSonInversos() {
        assertEquals("M-001", MissionCode.format(1))
        assertEquals("M-042", MissionCode.format(42))
        assertEquals("M-1000", MissionCode.format(1000))
        assertEquals(7, MissionCode.parse("M-007"))
        assertEquals(1000, MissionCode.parse("M-1000"))
    }

    @Test
    fun loQueNoTieneElFormatoSeIgnora() {
        assertNull(MissionCode.parse(""))
        assertNull(MissionCode.parse("X-001"))
        assertNull(MissionCode.parse("M-"))
        assertNull(MissionCode.parse("M-001 "))
    }

    @Test
    fun conLaBaseVaciaElUltimoNumeroEsCero() {
        assertEquals(0, MissionCode.lastNumber(emptyList()))
    }

    @Test
    fun conHistorialSeTomaElMayorAunqueHayaCodigosRepetidos() {
        // Instalaciones anteriores reiniciaban la numeración: M-001, M-002, M-001, M-003…
        assertEquals(3, MissionCode.lastNumber(listOf("M-001", "M-002", "M-001", "M-003", "M-001")))
        assertEquals(5, MissionCode.lastNumber(listOf("M-005", "basura", "M-002")))
    }
}
