package com.uade.robotcierre.ui.components

import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.ui.label

/** Texto, estado visual y si lleva spinner de un chip. */
data class ChipSpec(val label: String, val tone: Tone, val spinner: Boolean = false)

fun ConnectionState.chip() = ChipSpec(
    label(),
    when (this) {
        ConnectionState.CONNECTED -> Tone.OK
        ConnectionState.DEGRADED -> Tone.WARNING
        ConnectionState.DISCONNECTED -> Tone.ERROR
    }
)

fun CommandStatus.chip() = when (this) {
    CommandStatus.PENDING -> ChipSpec(label(), Tone.NEUTRAL, spinner = true)
    CommandStatus.CONFIRMED -> ChipSpec(label(), Tone.OK)
    CommandStatus.REJECTED -> ChipSpec(label(), Tone.ERROR)
    CommandStatus.NO_RESPONSE -> ChipSpec(label(), Tone.WARNING)
}

fun ZoneStatus.chip() = ChipSpec(
    label(),
    when (this) {
        ZoneStatus.PENDING -> Tone.NEUTRAL
        ZoneStatus.FREE -> Tone.OK
        ZoneStatus.PERSON_FOUND -> Tone.ERROR
        ZoneStatus.NOT_VERIFIABLE -> Tone.WARNING
        ZoneStatus.MANUAL_REVIEW -> Tone.WARNING
        ZoneStatus.VERIFIED_MANUALLY -> Tone.INFO
    }
)

fun MissionResult.chip() = ChipSpec(
    label(),
    when (this) {
        MissionResult.IN_PROGRESS -> Tone.INFO
        MissionResult.COMPLETED -> Tone.OK
        MissionResult.CANCELLED, MissionResult.RETURNED_EARLY -> Tone.WARNING
        MissionResult.INTERRUPTED -> Tone.ERROR
    }
)

fun Severity.tone() = when (this) {
    Severity.INFO -> Tone.INFO
    Severity.WARNING -> Tone.WARNING
    Severity.CRITICAL -> Tone.ERROR
}

fun EventType.defaultTone() = when (this) {
    EventType.PERSON_DETECTED, EventType.CONNECTION_LOST -> Tone.ERROR
    EventType.ZONE_NOT_VERIFIABLE, EventType.LOW_BATTERY, EventType.OBSTACLE -> Tone.WARNING
    EventType.MISSION_COMPLETE -> Tone.INFO
}
