package com.uade.robotcierre.viewmodel

import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.MissionResult
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.ZoneStatus
import com.uade.robotcierre.repository.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Traduce lo que ve el ViewModel (telemetría, eventos, órdenes confirmadas) en escrituras al
 * historial. Todas pasan por una sola cola, así que se guardan en el mismo orden en que ocurrieron
 * (el evento se inserta antes de marcarlo como reconocido).
 *
 * Una ronda se abre cuando aparece un `missionId` y se cierra cuando desaparece (el robot llegó
 * a la base). El resultado se deduce así: sin zonas pendientes (o con MISSION_COMPLETE) = COMPLETED;
 * si no, lo que decidió el operador (CANCELLED o RETURNED_EARLY).
 *
 * @param onError se llama si una escritura falla; el registro sigue con la operación siguiente.
 */
class HistoryRecorder(
    private val history: HistoryRepository,
    scope: CoroutineScope,
    private val clock: () -> Long,
    private val onError: (Throwable) -> Unit = {}
) {
    private sealed interface Op {
        data class Tel(val telemetry: Telemetry, val at: Long) : Op
        data class Event(val event: RobotEvent, val at: Long) : Op
        data class AlertClosed(val eventId: String) : Op
        data class CommandConfirmed(val type: CommandType) : Op
    }

    private val ops = Channel<Op>(Channel.UNLIMITED)

    // Estado: solo lo toca el consumidor de la cola.
    private var missionRowId: Long? = null
    private var missionCode: String? = null
    private var persistedVerified = 0
    private var completedSeen = false
    private var endReason: MissionResult? = null
    private var lastClosedId: Long? = null
    private val eventRows = HashMap<String, Long>()

    init {
        scope.launch {
            attempt { history.closeInterruptedMissions(clock()) }
            for (op in ops) attempt { process(op) }
        }
    }

    fun onTelemetry(t: Telemetry) {
        ops.trySend(Op.Tel(t, clock()))
    }

    fun onEvent(event: RobotEvent) {
        ops.trySend(Op.Event(event, clock()))
    }

    fun onAlertClosed(eventId: String) {
        ops.trySend(Op.AlertClosed(eventId))
    }

    fun onCommandConfirmed(type: CommandType) {
        ops.trySend(Op.CommandConfirmed(type))
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

    private suspend fun process(op: Op) {
        when (op) {
            is Op.Tel -> processTelemetry(op.telemetry, op.at)
            is Op.Event -> {
                // El aviso de fin de ronda y el último paquete (que cierra la ronda) salen en el mismo
                // segundo y pueden llegar en cualquier orden: el aviso siempre es de la última ronda.
                val completion = op.event.type == EventType.MISSION_COMPLETE
                val target = missionRowId ?: if (completion) lastClosedId else null
                eventRows[op.event.eventId] = history.insertEvent(target, op.event, op.at)
                if (completion && missionRowId != null) completedSeen = true
            }
            is Op.AlertClosed -> eventRows[op.eventId]?.let { history.markEventAcknowledged(it) }
            is Op.CommandConfirmed -> if (missionRowId != null) {
                when (op.type) {
                    CommandType.CANCEL_MISSION -> endReason = MissionResult.CANCELLED
                    CommandType.RETURN_TO_SAFE_POINT -> endReason = MissionResult.RETURNED_EARLY
                    else -> Unit
                }
            }
        }
    }

    private suspend fun processTelemetry(t: Telemetry, at: Long) {
        val code = t.missionId
        if (code != null) {
            if (missionRowId != null && missionCode != code) closeCurrent(at, MissionResult.INTERRUPTED, persistedVerified)
            if (missionRowId == null) {
                lastClosedId = null
                missionRowId = history.openMission(code, at)
                missionCode = code
                persistedVerified = 0
                completedSeen = false
                endReason = null
            }
            if (t.zonesVerified != persistedVerified) {
                history.updateZonesVerified(missionRowId!!, t.zonesVerified)
                persistedVerified = t.zonesVerified
            }
        } else if (missionRowId != null) {
            val completed = completedSeen || t.zonesStatus.values.none { it == ZoneStatus.PENDING }
            val result = when {
                completed -> MissionResult.COMPLETED
                else -> endReason ?: MissionResult.RETURNED_EARLY
            }
            closeCurrent(at, result, t.zonesVerified)
        } else {
            // Revisión manual registrada después de terminar: la ronda cerrada suma esa zona.
            val closed = lastClosedId
            if (closed != null && t.zonesVerified != persistedVerified) {
                history.updateZonesVerified(closed, t.zonesVerified)
                persistedVerified = t.zonesVerified
            }
        }
    }

    private suspend fun closeCurrent(at: Long, result: MissionResult, zonesVerified: Int) {
        val id = missionRowId ?: return
        history.closeMission(id, at, result, zonesVerified)
        lastClosedId = id
        persistedVerified = zonesVerified
        missionRowId = null
        missionCode = null
    }
}
