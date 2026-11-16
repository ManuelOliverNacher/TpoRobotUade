package com.uade.robotcierre.repository

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Telemetry
import kotlinx.coroutines.flow.Flow

/**
 * Contrato entre la app y el robot (telemetría, eventos y comandos).
 * Desacopla el ViewModel de la implementación simulada. El contrato es una propuesta
 * del grupo, NO un protocolo oficial de Unitree.
 */
interface RobotRepository {

    /**
     * Telemetría emitida cada ~1 s. No emite nada hasta que llega el primer paquete
     * (no hay valor inicial ni null).
     */
    val telemetry: Flow<Telemetry>

    /** Eventos y alertas nuevos, a medida que ocurren (hechos puntuales, no estado). */
    val events: Flow<RobotEvent>

    /**
     * Envía una orden y suspende hasta tener respuesta. Si no llega en
     * [Command.expiresInSeconds], devuelve [com.uade.robotcierre.model.CommandStatus.NO_RESPONSE]
     * (no lanza excepción). Que se envíe no significa que se ejecutó: mirar el estado devuelto.
     */
    suspend fun sendCommand(command: Command): CommandResponse
}
