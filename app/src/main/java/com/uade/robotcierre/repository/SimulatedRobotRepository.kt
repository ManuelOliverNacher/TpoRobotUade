package com.uade.robotcierre.repository

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Telemetry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Implementación simulada de [RobotRepository]. Envuelve a [SimulationEngine] con
 * corrutinas: emite telemetría cada 1 s y responde las órdenes con una latencia de 1–2 s.
 * Los valores son inventados/derivados, NO mediciones del robot real.
 *
 * Mientras no hay conexión no se emite telemetría (la UI detecta el dato antiguo por sí sola)
 * y las órdenes terminan en NO_RESPONSE al vencer su plazo.
 */
class SimulatedRobotRepository(
    scope: CoroutineScope,
    private val config: SimulationConfig = SimulationConfig(),
    clock: () -> Long = System::currentTimeMillis
) : RobotRepository, SimulationControls {

    private val _telemetry = MutableSharedFlow<Telemetry>(replay = 1)
    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 64)
    private val engine = SimulationEngine(config, clock) { _events.tryEmit(it) }
    private val commandCount = AtomicInteger(0)

    override val telemetry: Flow<Telemetry> = _telemetry.asSharedFlow()
    override val events: Flow<RobotEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            while (isActive) {
                engine.tick()?.let { _telemetry.emit(it) }
                delay(config.tickMillis)
            }
        }
    }

    override suspend fun sendCommand(command: Command): CommandResponse {
        val n = commandCount.getAndIncrement()
        val latency = config.baseLatencyMs + config.latencyStepMs * (n % config.latencySteps)
        val response = withTimeoutOrNull(command.expiresInSeconds * 1_000L) {
            delay(latency)
            // Sin conexión el robot nunca responde: se espera hasta que venza el plazo.
            if (!engine.isConnected) awaitCancellation()
            engine.handle(command)
        }
        return response ?: CommandResponse(
            commandId = command.commandId,
            status = CommandStatus.NO_RESPONSE,
            reason = "Sin respuesta del robot en ${command.expiresInSeconds} s."
        )
    }

    override fun forceDisconnect() = engine.forceDisconnect()
    override fun forceReconnect() = engine.forceReconnect()
    override fun rejectNextCommand() = engine.rejectNextCommand()
    override fun forceLowBattery() = engine.forceLowBattery()
    override fun forcePersonDetected() = engine.forcePersonDetected()
    override fun forceZoneNotVerifiable() = engine.forceZoneNotVerifiable()
}
