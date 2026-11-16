package com.uade.robotcierre.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneTest {
    @Test
    fun hayExactamenteSeisZonasEnOrden() {
        assertEquals(Zone.TOTAL, Zone.entries.size)
        assertEquals(
            listOf(
                Zone.HALL, Zone.CORRIDOR, Zone.RESTROOM_DOOR,
                Zone.PERGOLA, Zone.LAWN, Zone.ESPLANADE
            ),
            Zone.entries.toList()
        )
    }

    @Test
    fun checkpointsVanDe1a6() {
        assertEquals((1..6).toList(), Zone.entries.map { it.checkpoint })
    }
}
