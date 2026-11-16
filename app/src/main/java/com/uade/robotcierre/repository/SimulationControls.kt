package com.uade.robotcierre.repository

/**
 * Acciones del panel de simulación (solo modo demo) para forzar los escenarios.
 * Lo implementa únicamente el simulador. El ViewModel la recibe como dependencia
 * opcional (`SimulationControls?`) y expone estas acciones como eventos propios;
 * la UI nunca toca el simulador directamente.
 */
interface SimulationControls {
    fun forceDisconnect()
    fun forceReconnect()
    fun rejectNextCommand()
    fun forceLowBattery()
    fun forcePersonDetected()
    fun forceZoneNotVerifiable()
}
