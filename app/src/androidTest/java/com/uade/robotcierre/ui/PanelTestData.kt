package com.uade.robotcierre.ui

import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.viewmodel.AlertUi
import com.uade.robotcierre.viewmodel.BatteryLevel
import com.uade.robotcierre.viewmodel.CommandRecord
import com.uade.robotcierre.viewmodel.CommandRules
import com.uade.robotcierre.viewmodel.RuleInput
import com.uade.robotcierre.viewmodel.UiState

fun telemetryOf(
    state: RobotState = RobotState.IDLE,
    zone: Zone? = null,
    battery: Int = 100,
    moving: Boolean = false,
    people: Int = 0,
    statuses: Map<Zone, ZoneStatus> = Zone.entries.associateWith { ZoneStatus.PENDING },
    localization: LocalizationQuality = LocalizationQuality.GOOD
) = Telemetry(
    robotId = "go2-edu-01",
    timestamp = 1_700_000_000_000L,
    sequence = 1,
    connection = ConnectionState.CONNECTED,
    batteryPercent = battery,
    estimatedMinutes = battery / 15,
    state = state,
    missionId = if (state == RobotState.IDLE) null else "M-001",
    checkpoint = zone?.checkpoint ?: 0,
    zone = zone,
    moving = moving,
    speedMs = if (moving) 0.8 else 0.0,
    activeAlerts = 0,
    peopleDetected = people,
    zonesVerified = statuses.values.count { it == ZoneStatus.FREE || it == ZoneStatus.VERIFIED_MANUALLY },
    zonesStatus = statuses,
    localizationQuality = localization
)

/** Arma un [UiState] con la disponibilidad calculada por las reglas reales del ViewModel. */
fun uiStateOf(
    telemetry: Telemetry?,
    stale: Boolean = false,
    alerts: List<AlertUi> = emptyList(),
    commands: List<CommandRecord> = emptyList(),
    missions: List<MissionRecord> = emptyList(),
    selectedMissionId: Long? = null,
    selectedMissionEvents: List<EventRecord> = emptyList(),
    eventsOutsideMissions: List<EventRecord> = emptyList(),
    recovered: Boolean = false,
    simulationAvailable: Boolean = false,
    linkLost: Boolean = false
): UiState {
    val connected = telemetry != null && !stale && !linkLost &&
        telemetry.connection != ConnectionState.DISCONNECTED
    val input = RuleInput(
        telemetry = telemetry,
        connected = connected,
        activeAlerts = alerts.filter { !it.closed }.map { it.event },
        pendingTypes = commands.filter { it.status == CommandStatus.PENDING }.map { it.command.type }.toSet()
    )
    return UiState(
        telemetry = telemetry,
        lastUpdateMillis = telemetry?.timestamp,
        isStale = stale,
        connectionEffective = if (telemetry == null || stale || linkLost) {
            ConnectionState.DISCONNECTED
        } else {
            telemetry.connection
        },
        alerts = alerts,
        commands = commands,
        availability = CommandType.entries.associateWith { CommandRules.evaluate(it, input) },
        zonesNeedingReview = CommandRules.zonesNeedingReview(telemetry),
        missions = missions,
        selectedMissionId = selectedMissionId,
        selectedMissionEvents = selectedMissionEvents,
        eventsOutsideMissions = eventsOutsideMissions,
        connectionRecovered = recovered,
        simulationAvailable = simulationAvailable,
        batteryLevel = telemetry?.let { BatteryLevel.of(it.batteryPercent) } ?: BatteryLevel.OK
    )
}
