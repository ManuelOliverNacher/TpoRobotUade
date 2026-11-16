package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.repository.HistoryRepository
import com.uade.robotcierre.repository.RobotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun testTelemetry(
    state: RobotState = RobotState.IDLE,
    battery: Int = 100,
    connection: ConnectionState = ConnectionState.CONNECTED,
    zone: Zone? = null,
    timestamp: Long = 0L,
    sequence: Long = 1L,
    missionId: String? = if (state == RobotState.IDLE) null else "M-001",
    activeAlerts: Int = 0,
    statuses: Map<Zone, ZoneStatus> = Zone.entries.associateWith { ZoneStatus.PENDING }
) = Telemetry(
    robotId = "go2-edu-01",
    timestamp = timestamp,
    sequence = sequence,
    connection = connection,
    batteryPercent = battery,
    estimatedMinutes = 5,
    state = state,
    missionId = missionId,
    checkpoint = zone?.checkpoint ?: 0,
    zone = zone,
    moving = false,
    speedMs = 0.0,
    activeAlerts = activeAlerts,
    peopleDetected = 0,
    zonesVerified = statuses.values.count {
        it == ZoneStatus.FREE || it == ZoneStatus.VERIFIED_MANUALLY
    },
    zonesStatus = statuses,
    localizationQuality = LocalizationQuality.GOOD
)

fun testEvent(
    id: String = "E-001",
    type: EventType = EventType.PERSON_DETECTED,
    severity: Severity = Severity.CRITICAL,
    zone: Zone? = null,
    acknowledged: Boolean = false
) = RobotEvent(id, type, severity, zone, "detalle de prueba", acknowledged)

/** Repositorio falso controlable desde el test. */
class FakeRobotRepository : RobotRepository {
    val telemetryFlow = MutableSharedFlow<Telemetry>(replay = 1)
    val eventsFlow = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 64)
    val sent = mutableListOf<Command>()
    var responder: suspend (Command) -> CommandResponse =
        { CommandResponse(it.commandId, CommandStatus.CONFIRMED) }

    override val telemetry: Flow<Telemetry> = telemetryFlow
    override val events: Flow<RobotEvent> = eventsFlow

    override suspend fun sendCommand(command: Command): CommandResponse {
        sent += command
        return responder(command)
    }
}

/** Historial en memoria, con las mismas reglas de orden que Room. */
class FakeHistoryRepository : HistoryRepository {
    private val missionsState = MutableStateFlow<List<MissionRecord>>(emptyList())
    private val eventsState = MutableStateFlow<List<EventRecord>>(emptyList())
    private var nextMissionId = 1L
    private var nextEventId = 1L

    /** Demora artificial de cada inserción, para probar el orden de las escrituras. */
    var insertDelayMs = 0L

    /** Si es true, la próxima escritura falla una vez. */
    var failNext = false

    val missionsNow: List<MissionRecord> get() = missionsState.value
    val eventsNow: List<EventRecord> get() = eventsState.value

    fun seed(mission: MissionRecord) {
        missionsState.value = listOf(mission) + missionsState.value
    }

    override val missions = missionsState
    override val eventsOutsideMissions =
        eventsState.map { l -> l.filter { it.missionId == null }.sortedByDescending { it.id } }

    override fun eventsOf(missionId: Long) =
        eventsState.map { l -> l.filter { it.missionId == missionId }.sortedBy { it.id } }

    private fun maybeFail() {
        if (failNext) {
            failNext = false
            throw IllegalStateException("fallo simulado")
        }
    }

    private fun updateMission(id: Long, f: (MissionRecord) -> MissionRecord) {
        missionsState.value = missionsState.value.map { if (it.id == id) f(it) else it }
    }

    override suspend fun openMission(code: String, startedAt: Long): Long {
        maybeFail()
        val id = nextMissionId++
        missionsState.value = listOf(
            MissionRecord(id, code, startedAt, null, MissionResult.IN_PROGRESS, 0)
        ) + missionsState.value
        return id
    }

    override suspend fun closeMission(id: Long, endedAt: Long, result: MissionResult, zonesVerified: Int) {
        maybeFail()
        updateMission(id) { it.copy(endedAt = endedAt, result = result, zonesVerified = zonesVerified) }
    }

    override suspend fun updateZonesVerified(id: Long, zonesVerified: Int) {
        maybeFail()
        updateMission(id) { it.copy(zonesVerified = zonesVerified) }
    }

    override suspend fun insertEvent(
        missionId: Long?,
        event: com.uade.robotcierre.model.RobotEvent,
        timestamp: Long
    ): Long {
        if (insertDelayMs > 0) delay(insertDelayMs)
        maybeFail()
        val id = nextEventId++
        eventsState.value = eventsState.value + EventRecord(
            id, missionId, event.type, event.severity, event.zone, event.details, timestamp,
            event.acknowledged
        )
        return id
    }

    override suspend fun markEventAcknowledged(id: Long) {
        maybeFail()
        eventsState.value = eventsState.value.map { if (it.id == id) it.copy(acknowledged = true) else it }
    }

    override suspend fun closeInterruptedMissions(now: Long) {
        maybeFail()
        missionsState.value = missionsState.value.map {
            if (it.result == MissionResult.IN_PROGRESS) {
                it.copy(result = MissionResult.INTERRUPTED, endedAt = now)
            } else {
                it
            }
        }
    }
}
