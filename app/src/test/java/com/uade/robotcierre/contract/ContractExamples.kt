package com.uade.robotcierre.contract

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.repository.SimulatedRobotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime

/** 2026-11-15T22:41:05Z: hora ficticia fija para que los ejemplos sean reproducibles. */
const val EXAMPLE_EPOCH_MILLIS = 1_794_782_465_000L

/** Ejemplos del contrato: nombre → JSON. Salen de recorrer el simulador real (sin azar). */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.generateContractExamples(): Map<String, String> {
    val repo = SimulatedRobotRepository(backgroundScope, clock = { EXAMPLE_EPOCH_MILLIS + currentTime })
    val telemetry = mutableListOf<Telemetry>()
    val events = mutableListOf<RobotEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.telemetry.toList(telemetry) }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.events.toList(events) }
    advanceTimeBy(1_500)

    fun command(id: String, type: CommandType, zone: Zone? = null) =
        Command(id, type, EXAMPLE_EPOCH_MILLIS + currentTime, "operador-1", 10, zone)

    val examples = linkedMapOf<String, String>()
    examples["telemetria_en_reposo"] = ContractJson.encode(telemetry.first())

    // Orden confirmada: iniciar la ronda.
    val start = command("CMD-001", CommandType.START_MISSION)
    val confirmed = repo.sendCommand(start)
    examples["comando_iniciar_ronda"] = ContractJson.encode(start)
    examples["respuesta_confirmada"] = ContractJson.encode(confirmed)

    // Se avanza hasta la zona techada del patio y se fuerzan un aviso de batería y una persona.
    while (telemetry.last().zone != Zone.PERGOLA) advanceTimeBy(1_000)
    advanceTimeBy(2_000)
    repo.forceLowBattery()
    advanceTimeBy(6_000)
    repo.forcePersonDetected()
    advanceTimeBy(1_500)

    examples["evento_bateria_baja"] = ContractJson.encode(events.first { it.type == EventType.LOW_BATTERY })
    examples["evento_persona_detectada"] = ContractJson.encode(events.first { it.type == EventType.PERSON_DETECTED })
    examples["telemetria_con_persona_detectada"] =
        ContractJson.encode(telemetry.last { it.state == RobotState.PAUSED && it.peopleDetected > 0 })

    // Orden con zona (el baño requiere revisión manual) y orden rechazada por el robot.
    val review = command("CMD-002", CommandType.MANUAL_REVIEW, Zone.RESTROOM_DOOR)
    check(repo.sendCommand(review).status == CommandStatus.CONFIRMED)
    examples["comando_revision_manual"] = ContractJson.encode(review)

    val resume = command("CMD-003", CommandType.RESUME_MISSION)
    val rejected = repo.sendCommand(resume)
    check(rejected.status == CommandStatus.REJECTED)
    examples["respuesta_rechazada"] = ContractJson.encode(rejected)

    // Sin conexión el robot no responde: la orden vence a los 10 s (NO_RESPONSE).
    repo.forceDisconnect()
    val stop = command("CMD-004", CommandType.OPERATIONAL_STOP)
    val noResponse: CommandResponse = repo.sendCommand(stop)
    check(noResponse.status == CommandStatus.NO_RESPONSE)
    examples["respuesta_sin_respuesta"] = ContractJson.encode(noResponse)
    return examples
}
