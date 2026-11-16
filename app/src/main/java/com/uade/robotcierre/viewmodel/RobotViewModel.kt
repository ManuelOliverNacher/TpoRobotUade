package com.uade.robotcierre.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.repository.HistoryRepository
import com.uade.robotcierre.repository.RobotRepository
import com.uade.robotcierre.repository.SimulationControls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del panel de supervisión. La UI solo observa [uiState] y envía [RobotUiEvent].
 * Las reglas de habilitación se evalúan acá (con [CommandRules]), nunca en la UI.
 * [simulation] es opcional: solo existe en modo demo. [history] también: si está, el ViewModel
 * guarda las rondas y eventos (vía [HistoryRecorder]) y expone el historial en [UiState];
 * las pantallas leen el historial únicamente a través de este ViewModel.
 *
 * Se usa siempre desde el hilo principal (viewModelScope), por eso el estado interno es un `var`.
 */
class RobotViewModel(
    private val repository: RobotRepository,
    private val simulation: SimulationControls? = null,
    private val history: HistoryRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val operatorId: String = "operador-1",
    private val historyOnError: (Throwable) -> Unit = {},
    private val onDispose: () -> Unit = {}
) : ViewModel() {

    private data class Core(
        val telemetry: Telemetry? = null,
        val stale: Boolean = false,
        val alerts: List<AlertUi> = emptyList(),
        val commands: List<CommandRecord> = emptyList(),
        val confirmation: CommandType? = null,
        val linkLost: Boolean = false,
        val linkLostAt: Long = 0L,
        val recovered: Boolean = false,
        val notice: SimulationNotice? = null,
        val missions: List<MissionRecord> = emptyList(),
        val eventsOutsideMissions: List<EventRecord> = emptyList(),
        val selectedMissionId: Long? = null,
        val selectedMissionEvents: List<EventRecord> = emptyList()
    )

    private var core = Core()
    private val _uiState = MutableStateFlow(buildUiState(core))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var commandCounter = 0
    private var staleJob: Job? = null
    private var selectedJob: Job? = null
    private var recoveredJob: Job? = null
    private var noticeJob: Job? = null
    private var noticeCounter = 0
    private val recorder = history?.let { HistoryRecorder(it, viewModelScope, clock, historyOnError) }

    init {
        // Suscripción desde la creación: no se pierden eventos aunque la UI todavía no observe.
        viewModelScope.launch { repository.telemetry.collect { onTelemetry(it) } }
        viewModelScope.launch { repository.events.collect { onRobotEvent(it) } }
        if (history != null) {
            viewModelScope.launch { history.missions.collect { m -> update { it.copy(missions = m) } } }
            viewModelScope.launch {
                history.eventsOutsideMissions.collect { e -> update { it.copy(eventsOutsideMissions = e) } }
            }
        }
    }

    fun onEvent(event: RobotUiEvent) {
        when (event) {
            is RobotUiEvent.RequestCommand -> requestCommand(event.type, event.zone)
            RobotUiEvent.ConfirmPending -> confirmPending()
            RobotUiEvent.DismissConfirmation -> update { it.copy(confirmation = null) }
            is RobotUiEvent.Simulate -> simulate(event.action)
            is RobotUiEvent.SelectMission -> selectMission(event.missionId)
        }
    }

    private fun selectMission(missionId: Long?) {
        selectedJob?.cancel()
        selectedJob = null
        update { it.copy(selectedMissionId = missionId, selectedMissionEvents = emptyList()) }
        if (missionId != null && history != null) {
            selectedJob = viewModelScope.launch {
                history.eventsOf(missionId).collect { e -> update { it.copy(selectedMissionEvents = e) } }
            }
        }
    }

    // ------------------------------------------------------------ telemetría

    private fun onTelemetry(t: Telemetry) {
        recorder?.onTelemetry(t)
        staleJob?.cancel()
        val wasRecovered = core.recovered
        update { c ->
            var alerts = c.alerts
            var linkLost = c.linkLost
            // Primer paquete generado DESPUÉS de perder el enlace: se cierra CONNECTION_LOST.
            // (Un paquete que ya estaba en vuelo tiene timestamp anterior y no cuenta.)
            if (linkLost && t.timestamp > c.linkLostAt) {
                alerts = alerts.map {
                    if (it.event.type == EventType.CONNECTION_LOST) it.copy(closed = true) else it
                }
                linkLost = false
            }
            // Red de seguridad: si el robot dice que no hay alertas activas, se cierran las que
            // ya conocía al generar el paquete (no las que llegaron después).
            if (t.activeAlerts == 0) {
                alerts = alerts.map {
                    if (!it.closed && it.receivedAt < t.timestamp) it.copy(closed = true) else it
                }
            }
            // Volvió la conexión: había una pérdida o datos antiguos y ahora llega un paquete válido.
            val recovered = if ((c.stale || c.linkLost) && !linkLost) true else c.recovered
            c.copy(telemetry = t, stale = false, alerts = alerts, linkLost = linkLost, recovered = recovered)
        }
        if (!wasRecovered && core.recovered) {
            recoveredJob?.cancel()
            recoveredJob = viewModelScope.launch {
                delay(RECOVERED_BANNER_MILLIS)
                update { it.copy(recovered = false) }
            }
        }
        // Pasados más de 5 s sin paquete nuevo, los datos se marcan como antiguos.
        staleJob = viewModelScope.launch {
            delay(STALE_AFTER_MILLIS + 1)
            update { it.copy(stale = true, recovered = false) }
        }
    }

    private fun onRobotEvent(event: RobotEvent) {
        recorder?.onEvent(event)
        val now = clock()
        update { c ->
            val alert = AlertUi(event, receivedAt = now, closed = event.acknowledged)
            val lost = event.type == EventType.CONNECTION_LOST
            c.copy(
                alerts = (listOf(alert) + c.alerts).take(MAX_ALERTS),
                linkLost = c.linkLost || lost,
                linkLostAt = if (lost) now else c.linkLostAt,
                recovered = c.recovered && !lost
            )
        }
    }

    // --------------------------------------------------------------- órdenes

    private fun requestCommand(type: CommandType, zone: Zone?) {
        if (!availabilityOf(core, type, zone).enabled) return // deshabilitada: no se envía
        if (CommandRules.requiresConfirmation(type)) {
            update { it.copy(confirmation = type) }
        } else {
            send(type, zone)
        }
    }

    private fun confirmPending() {
        val type = core.confirmation ?: return
        update { it.copy(confirmation = null) }
        if (availabilityOf(core, type).enabled) send(type, null) // se revalida al confirmar
    }

    private fun send(type: CommandType, zone: Zone?) {
        commandCounter++
        val command = Command(
            commandId = "CMD-%03d".format(commandCounter),
            type = type,
            issuedAt = clock(),
            operatorId = operatorId,
            expiresInSeconds = EXPIRES_IN_SECONDS,
            zone = zone
        )
        // Al enviar una orden el operador ya vio el aviso de conexión recuperada.
        recoveredJob?.cancel()
        // Desde el envío hasta la respuesta la orden figura como PENDING.
        update {
            it.copy(
                recovered = false,
                commands = (listOf(CommandRecord(command, CommandStatus.PENDING)) + it.commands)
                    .take(MAX_COMMANDS)
            )
        }
        viewModelScope.launch {
            val response = try {
                repository.sendCommand(command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CommandResponse(command.commandId, CommandStatus.NO_RESPONSE, "Error de comunicación.")
            }
            onResponse(command, response)
        }
    }

    private fun onResponse(command: Command, response: CommandResponse) {
        if (response.status == CommandStatus.CONFIRMED) recorder?.onCommandConfirmed(command.type)
        val now = clock()
        update { c ->
            var alerts = c.alerts
            if (response.status == CommandStatus.CONFIRMED) {
                alerts = when (command.type) {
                    // Igual que el robot: reconocer cierra todas las alertas activas.
                    CommandType.ACK_ALERT -> alerts.map { it.copy(closed = true) }
                    // La revisión manual cierra las alertas de esa zona.
                    CommandType.MANUAL_REVIEW -> alerts.map {
                        if (it.event.type == EventType.ZONE_NOT_VERIFIABLE && it.event.zone == command.zone) {
                            it.copy(closed = true)
                        } else {
                            it
                        }
                    }
                    else -> alerts
                }
            }
            c.copy(
                alerts = alerts,
                commands = c.commands.map {
                    if (it.command.commandId == command.commandId) {
                        it.copy(status = response.status, reason = response.reason, resolvedAt = now)
                    } else {
                        it
                    }
                }
            )
        }
    }

    // ------------------------------------------------------------ simulación

    private fun simulate(action: SimulationAction) {
        val sim = simulation ?: return
        when (action) {
            SimulationAction.DISCONNECT -> sim.forceDisconnect()
            SimulationAction.RECONNECT -> sim.forceReconnect()
            SimulationAction.REJECT_NEXT_COMMAND -> {
                sim.rejectNextCommand()
                showNotice("La próxima orden será rechazada.")
            }
            SimulationAction.LOW_BATTERY -> sim.forceLowBattery()
            SimulationAction.PERSON_DETECTED -> sim.forcePersonDetected()
            SimulationAction.ZONE_NOT_VERIFIABLE -> sim.forceZoneNotVerifiable()
        }
    }

    /** Muestra un aviso breve; se retira solo pasados [NOTICE_MILLIS]. */
    private fun showNotice(text: String) {
        noticeCounter++
        update { it.copy(notice = SimulationNotice(noticeCounter, text)) }
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MILLIS)
            update { it.copy(notice = null) }
        }
    }

    // ---------------------------------------------------------------- estado

    private fun update(block: (Core) -> Core) {
        val before = core
        core = block(core)
        notifyClosedAlerts(before, core)
        _uiState.value = buildUiState(core)
    }

    /** Las alertas que pasaron de abiertas a cerradas se marcan como reconocidas en el historial. */
    private fun notifyClosedAlerts(before: Core, after: Core) {
        val rec = recorder ?: return
        val wasOpen = before.alerts.filter { !it.closed }.map { it.event.eventId }.toSet()
        if (wasOpen.isEmpty()) return
        after.alerts.filter { it.closed && it.event.eventId in wasOpen }
            .forEach { rec.onAlertClosed(it.event.eventId) }
    }

    private fun connectedEffective(c: Core): Boolean {
        val t = c.telemetry
        return t != null && !c.stale && !c.linkLost && t.connection != ConnectionState.DISCONNECTED
    }

    private fun ruleInput(c: Core) = RuleInput(
        telemetry = c.telemetry,
        connected = connectedEffective(c),
        activeAlerts = c.alerts.filter { !it.closed }.map { it.event },
        pendingTypes = c.commands.filter { it.status == CommandStatus.PENDING }
            .map { it.command.type }.toSet()
    )

    private fun availabilityOf(c: Core, type: CommandType, zone: Zone? = null) =
        CommandRules.evaluate(type, ruleInput(c), zone)

    private fun buildUiState(c: Core): UiState {
        val t = c.telemetry
        val input = ruleInput(c)
        return UiState(
            telemetry = t,
            lastUpdateMillis = t?.timestamp,
            isStale = c.stale,
            connectionEffective = if (t == null || c.stale || c.linkLost) {
                ConnectionState.DISCONNECTED
            } else {
                t.connection
            },
            alerts = c.alerts,
            commands = c.commands,
            availability = CommandType.entries.associateWith { CommandRules.evaluate(it, input) },
            zonesNeedingReview = CommandRules.zonesNeedingReview(t),
            confirmation = c.confirmation,
            simulationAvailable = simulation != null,
            connectionRecovered = c.recovered,
            simulationNotice = c.notice,
            missions = c.missions,
            eventsOutsideMissions = c.eventsOutsideMissions,
            selectedMissionId = c.selectedMissionId,
            selectedMissionEvents = c.selectedMissionEvents,
            batteryLevel = t?.let { BatteryLevel.of(it.batteryPercent) } ?: BatteryLevel.OK
        )
    }

    /** Se llama al destruirse el ViewModel (por ejemplo, para detener el simulador). */
    override fun onCleared() {
        onDispose()
    }

    companion object {
        /** Pasado este tiempo sin telemetría nueva los datos se marcan como antiguos. */
        const val STALE_AFTER_MILLIS = 5_000L

        /** El aviso de conexión recuperada se retira solo pasado este tiempo. */
        const val RECOVERED_BANNER_MILLIS = 10_000L

        /** Los avisos breves del panel de simulación se retiran solos pasado este tiempo. */
        const val NOTICE_MILLIS = 4_000L
        private const val EXPIRES_IN_SECONDS = 10
        private const val MAX_COMMANDS = 20
        private const val MAX_ALERTS = 100
    }
}
