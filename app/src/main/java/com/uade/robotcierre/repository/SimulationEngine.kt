package com.uade.robotcierre.repository

import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Motor determinista del simulador (sin corrutinas ni azar). Cada llamada a [tick] avanza
 * la simulación 1 segundo. Todos los valores son inventados/derivados, NO mediciones.
 *
 * Reglas principales:
 * - Recorre las 6 zonas en orden y vuelve a la base. El baño termina SIEMPRE en MANUAL_REVIEW.
 * - [forceDisconnect] pone la ronda en PAUSED; al reconectar espera RESUME_MISSION del operador.
 * - Tras ACK_ALERT la zona con persona queda en PERSON_FOUND (no pasa a FREE).
 * - La localización DEGRADED vuelve a GOOD al salir de la zona o al registrar MANUAL_REVIEW de ella.
 * - ACK_ALERT reconoce todas las alertas activas a la vez.
 */
class SimulationEngine(
    private val config: SimulationConfig = SimulationConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val onEvent: (RobotEvent) -> Unit = {}
) : SimulationControls {

    private enum class Phase { TRAVEL, VERIFY }

    private var connected = true
    private var state = RobotState.IDLE
    private var battery = config.initialBatteryPercent
    private var sequence = 0L
    private var missionCounter = 0
    private var eventCounter = 0

    private var missionActive = false
    private var missionId: String? = null
    private var returning = false
    private var routeCompleted = false
    private var zoneIndex = 0
    private var phase = Phase.TRAVEL
    private var phaseElapsed = 0
    private var returnElapsed = 0

    private var routeBlocked = false
    private var rejectNext = false
    private var lowBatteryWarned = false
    private var peopleDetected = 0
    private var localization = LocalizationQuality.GOOD
    private var degradedZone: Zone? = null

    private val zoneStatus = LinkedHashMap<Zone, ZoneStatus>().apply {
        Zone.entries.forEach { put(it, ZoneStatus.PENDING) }
    }
    private val activeAlerts = mutableListOf<RobotEvent>()

    private val currentZone: Zone get() = Zone.entries[zoneIndex]

    /** true si el enlace con el robot está activo. */
    val isConnected: Boolean @Synchronized get() = connected

    // ---------------------------------------------------------------- tick

    /** Avanza 1 s. Devuelve la telemetría, o null si no hay conexión (no se emite nada). */
    @Synchronized
    fun tick(): Telemetry? {
        advance()
        if (!connected) return null
        sequence++
        return buildTelemetry()
    }

    private fun advance() {
        val consuming = state == RobotState.ON_MISSION || state == RobotState.RETURNING
        val drain = if (consuming) config.missionDrainPerSecond else config.idleDrainPerSecond
        battery = max(0.0, battery - drain)
        when (state) {
            RobotState.ON_MISSION -> advanceMission()
            RobotState.RETURNING -> advanceReturn()
            else -> Unit
        }
        checkLowBattery()
    }

    private fun advanceMission() {
        phaseElapsed++
        when (phase) {
            Phase.TRAVEL -> if (phaseElapsed >= config.travelSeconds) {
                phase = Phase.VERIFY
                phaseElapsed = 0
            }
            Phase.VERIFY -> if (phaseElapsed >= config.verifySeconds) finishZone()
        }
    }

    private fun finishZone() {
        val zone = currentZone
        if (zoneStatus[zone] == ZoneStatus.PENDING) {
            if (zone == Zone.RESTROOM_DOOR) {
                // Privacidad: el robot nunca entra al baño, siempre requiere revisión manual.
                zoneStatus[zone] = ZoneStatus.MANUAL_REVIEW
                raise(
                    EventType.ZONE_NOT_VERIFIABLE, Severity.INFO, zone,
                    "${zone.label}: requiere revisión manual (privacidad, el robot no entra)."
                )
            } else {
                zoneStatus[zone] = ZoneStatus.FREE
            }
        }
        if (zoneIndex == Zone.entries.lastIndex) {
            routeCompleted = true
            startReturn()
        } else {
            leaveZone()
            zoneIndex++
            phase = Phase.TRAVEL
            phaseElapsed = 0
        }
    }

    private fun advanceReturn() {
        returnElapsed++
        if (returnElapsed >= config.returnSeconds) arriveAtBase()
    }

    private fun startReturn() {
        state = RobotState.RETURNING
        returning = true
        returnElapsed = 0
        leaveZone()
    }

    private fun arriveAtBase() {
        val completed = routeCompleted
        state = RobotState.IDLE
        missionActive = false
        missionId = null
        returning = false
        localization = LocalizationQuality.GOOD
        degradedZone = null
        if (completed) {
            val manual = zonesWith(ZoneStatus.MANUAL_REVIEW, ZoneStatus.NOT_VERIFIABLE)
            val people = zonesWith(ZoneStatus.PERSON_FOUND)
            val details = buildString {
                append("Ronda finalizada: ${zonesVerified()} de ${Zone.TOTAL} zonas verificadas.")
                if (manual.isNotEmpty()) append(" Requieren revisión manual: ${names(manual)}.")
                if (people.isNotEmpty()) append(" Persona hallada en: ${names(people)}.")
            }
            raise(EventType.MISSION_COMPLETE, Severity.INFO, null, details, active = false)
        }
    }

    /** Al salir de una zona, la localización y la detección de personas se normalizan. */
    private fun leaveZone() {
        localization = LocalizationQuality.GOOD
        degradedZone = null
        peopleDetected = 0
    }

    private fun checkLowBattery() {
        if (lowBatteryWarned || !missionActive || battery > config.lowBatteryWarningPercent) return
        lowBatteryWarned = true
        val pending = zonesWith(ZoneStatus.PENDING)
        val missing = if (pending.isEmpty()) {
            "Ya se recorrieron todas las zonas."
        } else {
            "Zonas sin visitar: ${names(pending)}."
        }
        raise(
            EventType.LOW_BATTERY, Severity.WARNING, null,
            "Batería al ${batteryPercent()} %. Volver a la base o cancelar la ronda. $missing"
        )
    }

    // ------------------------------------------------------------- comandos

    /**
     * Valida la orden contra el estado actual del robot y, si corresponde, la aplica.
     * (Es la defensa del lado del robot; la habilitación de botones la evalúa el ViewModel.)
     */
    @Synchronized
    fun handle(command: Command): CommandResponse {
        if (rejectNext) {
            rejectNext = false
            return reject(command, "Orden rechazada por el robot (simulación).")
        }
        return when (command.type) {
            CommandType.START_MISSION -> handleStart(command)
            CommandType.PAUSE_MISSION -> when (state) {
                RobotState.ON_MISSION -> {
                    state = RobotState.PAUSED
                    confirm(command)
                }
                RobotState.PAUSED -> reject(command, "La ronda ya está pausada.")
                else -> reject(command, "No hay una ronda en curso.")
            }
            CommandType.RESUME_MISSION -> when {
                state != RobotState.PAUSED -> reject(command, "La ronda no está pausada.")
                hasCriticalAlert() -> reject(command, "Hay una alerta sin reconocer.")
                else -> {
                    state = RobotState.ON_MISSION
                    confirm(command)
                }
            }
            CommandType.CANCEL_MISSION -> when (state) {
                RobotState.ON_MISSION, RobotState.PAUSED -> {
                    startReturn()
                    confirm(command, "Cancelada: el robot vuelve a la base.")
                }
                else -> reject(command, "La ronda ya finalizó (no hay ronda en curso ni pausada).")
            }
            CommandType.RETURN_TO_SAFE_POINT -> when {
                state == RobotState.IDLE -> reject(command, "El robot ya está en la base.")
                state == RobotState.RETURNING -> reject(command, "El robot ya está volviendo.")
                routeBlocked -> reject(command, "Ruta bloqueada por un obstáculo.")
                else -> {
                    startReturn()
                    confirm(command)
                }
            }
            CommandType.OPERATIONAL_STOP -> when (state) {
                RobotState.IDLE -> confirm(command, "El robot ya estaba en reposo en la base.")
                RobotState.STOPPED -> confirm(command, "El robot ya estaba detenido.")
                else -> {
                    state = RobotState.STOPPED
                    confirm(command)
                }
            }
            CommandType.ACK_ALERT -> if (activeAlerts.isEmpty()) {
                reject(command, "No hay alertas activas (ya estaban cerradas).")
            } else {
                activeAlerts.clear()
                confirm(command)
            }
            CommandType.MANUAL_REVIEW -> handleManualReview(command)
        }
    }

    private fun handleStart(command: Command): CommandResponse {
        when (state) {
            RobotState.ON_MISSION, RobotState.PAUSED, RobotState.RETURNING ->
                return reject(command, "Ya hay una ronda en curso.")
            RobotState.STOPPED ->
                return reject(command, "El robot está detenido: debe volver al punto seguro.")
            RobotState.FAULT -> return reject(command, "El robot tiene una falla.")
            RobotState.IDLE -> Unit
        }
        if (battery < config.minBatteryToStartPercent) {
            return reject(
                command,
                "Batería baja (${batteryPercent()} %): mínimo ${config.minBatteryToStartPercent.toInt()} % para iniciar."
            )
        }
        if (hasCriticalAlert()) return reject(command, "Hay una alerta crítica sin reconocer.")

        missionCounter++
        missionId = "M-%03d".format(missionCounter)
        missionActive = true
        returning = false
        routeCompleted = false
        zoneIndex = 0
        phase = Phase.TRAVEL
        phaseElapsed = 0
        returnElapsed = 0
        lowBatteryWarned = false
        peopleDetected = 0
        localization = LocalizationQuality.GOOD
        degradedZone = null
        Zone.entries.forEach { zoneStatus[it] = ZoneStatus.PENDING }
        activeAlerts.clear()
        state = RobotState.ON_MISSION
        return confirm(command)
    }

    private fun handleManualReview(command: Command): CommandResponse {
        val zone = command.zone ?: return reject(command, "Falta indicar la zona a revisar.")
        return when (zoneStatus.getValue(zone)) {
            ZoneStatus.MANUAL_REVIEW, ZoneStatus.NOT_VERIFIABLE -> {
                zoneStatus[zone] = ZoneStatus.VERIFIED_MANUALLY
                activeAlerts.removeAll { it.type == EventType.ZONE_NOT_VERIFIABLE && it.zone == zone }
                if (degradedZone == zone) {
                    localization = LocalizationQuality.GOOD
                    degradedZone = null
                }
                confirm(command)
            }
            ZoneStatus.FREE, ZoneStatus.VERIFIED_MANUALLY ->
                reject(command, "La zona ya está verificada.")
            ZoneStatus.PENDING -> reject(command, "La zona todavía no fue visitada.")
            ZoneStatus.PERSON_FOUND ->
                reject(command, "Hay una persona en la zona: no corresponde revisión manual.")
        }
    }

    // -------------------------------------------------- SimulationControls

    @Synchronized
    override fun forceDisconnect() {
        if (!connected) return
        connected = false
        val stopped = state == RobotState.ON_MISSION
        if (stopped) state = RobotState.PAUSED // el robot se detiene solo al perder las órdenes
        raise(
            EventType.CONNECTION_LOST, Severity.CRITICAL, null,
            "Se perdió la conexión con el robot." +
                if (stopped) " La ronda quedó en pausa: al reconectar hay que reanudarla." else ""
        )
    }

    @Synchronized
    override fun forceReconnect() {
        if (connected) return
        connected = true
        activeAlerts.removeAll { it.type == EventType.CONNECTION_LOST }
    }

    @Synchronized
    override fun rejectNextCommand() {
        rejectNext = true
    }

    @Synchronized
    override fun forceLowBattery() {
        battery = min(battery, config.forcedLowBatteryPercent)
    }

    @Synchronized
    override fun forcePersonDetected() {
        if (!inActiveZone()) return
        val zone = currentZone
        zoneStatus[zone] = ZoneStatus.PERSON_FOUND
        peopleDetected = 1
        state = RobotState.PAUSED // se detiene a distancia hasta que el operador decida
        raise(
            EventType.PERSON_DETECTED, Severity.CRITICAL, zone,
            "Persona detectada en ${zone.label}. El robot se detuvo a distancia."
        )
    }

    @Synchronized
    override fun forceZoneNotVerifiable() {
        if (!inActiveZone()) return
        val zone = currentZone
        if (zone == Zone.RESTROOM_DOOR || zoneStatus[zone] != ZoneStatus.PENDING) return
        zoneStatus[zone] = ZoneStatus.NOT_VERIFIABLE
        localization = LocalizationQuality.DEGRADED
        degradedZone = zone
        raise(
            EventType.ZONE_NOT_VERIFIABLE, Severity.WARNING, zone,
            "Localización degradada (lluvia simulada): ${zone.label} no se puede verificar. " +
                "Requiere revisión manual."
        )
    }

    /** Ayuda para tests/demo: bloquea o libera la ruta de regreso. No forma parte del panel. */
    @Synchronized
    fun setRouteBlocked(blocked: Boolean) {
        routeBlocked = blocked
    }

    // -------------------------------------------------------------- helpers

    private fun inActiveZone() = state == RobotState.ON_MISSION && missionActive && !returning

    private fun hasCriticalAlert() = activeAlerts.any { it.severity == Severity.CRITICAL }

    private fun zonesVerified() = zoneStatus.values.count {
        it == ZoneStatus.FREE || it == ZoneStatus.VERIFIED_MANUALLY
    }

    private fun zonesWith(vararg statuses: ZoneStatus) =
        Zone.entries.filter { zoneStatus[it] in statuses }

    private fun names(zones: List<Zone>) = zones.joinToString(", ") { it.label }

    private fun batteryPercent() = ceil(battery).toInt()

    private fun confirm(command: Command, reason: String? = null) =
        CommandResponse(command.commandId, CommandStatus.CONFIRMED, reason)

    private fun reject(command: Command, reason: String) =
        CommandResponse(command.commandId, CommandStatus.REJECTED, reason)

    /** Crea el evento; si [active] queda como alerta pendiente hasta ACK_ALERT. */
    private fun raise(
        type: EventType,
        severity: Severity,
        zone: Zone?,
        details: String,
        active: Boolean = true
    ) {
        eventCounter++
        val event = RobotEvent(
            eventId = "E-%03d".format(eventCounter),
            type = type,
            severity = severity,
            zone = zone,
            details = details,
            acknowledged = !active
        )
        if (active) activeAlerts += event
        onEvent(event)
    }

    private fun buildTelemetry(): Telemetry {
        val moving = (state == RobotState.ON_MISSION && phase == Phase.TRAVEL) ||
            state == RobotState.RETURNING
        val minutes = (battery / (config.missionDrainPerSecond * 60)).toInt()
        return Telemetry(
            robotId = config.robotId,
            timestamp = clock(),
            sequence = sequence,
            connection = ConnectionState.CONNECTED,
            batteryPercent = batteryPercent(),
            estimatedMinutes = minutes,
            state = state,
            missionId = missionId,
            checkpoint = if (missionActive) zoneIndex + 1 else 0,
            zone = if (missionActive && !returning) currentZone else null,
            moving = moving,
            speedMs = if (moving) config.walkingSpeedMs else 0.0,
            activeAlerts = activeAlerts.size,
            peopleDetected = peopleDetected,
            zonesVerified = zonesVerified(),
            zonesStatus = Zone.entries.associateWith { zoneStatus.getValue(it) },
            localizationQuality = localization
        )
    }
}
