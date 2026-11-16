package com.uade.robotcierre.ui

import com.uade.robotcierre.ui.components.PuntoEstado.COMPLETED
import com.uade.robotcierre.ui.components.PuntoEstado.CURRENT
import com.uade.robotcierre.ui.components.PuntoEstado.PENDING
import com.uade.robotcierre.ui.components.puntosDeRonda
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgresoPuntosTest {

    @Test
    fun sinRondaTodosLosPuntosEstanPendientes() {
        assertEquals(List(6) { PENDING }, puntosDeRonda(checkpoint = 0, total = 6, onZone = false))
    }

    @Test
    fun enRondaLosAnterioresEstanCompletosElActualResaltadoYLosDemasPendientes() {
        assertEquals(
            listOf(COMPLETED, COMPLETED, COMPLETED, CURRENT, PENDING, PENDING),
            puntosDeRonda(checkpoint = 4, total = 6, onZone = true)
        )
        assertEquals(
            listOf(CURRENT, PENDING, PENDING, PENDING, PENDING, PENDING),
            puntosDeRonda(checkpoint = 1, total = 6, onZone = true)
        )
    }

    @Test
    fun volviendoALaBaseLosSeisPuntosEstanCompletos() {
        assertEquals(List(6) { COMPLETED }, puntosDeRonda(checkpoint = 6, total = 6, onZone = false))
    }
}
