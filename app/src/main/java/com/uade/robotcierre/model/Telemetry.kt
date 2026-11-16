package com.uade.robotcierre.model

/**
 * Paquete de telemetría (contrato propuesto por el grupo, NO es un protocolo oficial de Unitree).
 * Todos los valores son simulados/derivados, no mediciones.
 *
 * @property timestamp epoch en milisegundos.
 * @property checkpoint 0 = sin misión; 1..6 = punto de control actual.
 * @property zone zona actual; null si el robot está en la base o sin misión.
 * @property missionId null si no hay misión.
 * @property activeAlerts cantidad de alertas activas (el detalle viaja por el flujo de eventos).
 * @property zonesStatus estado de cada una de las 6 zonas.
 */
data class Telemetry(
    val robotId: String,
    val timestamp: Long,
    val sequence: Long,
    val connection: ConnectionState,
    val batteryPercent: Int,
    val estimatedMinutes: Int,
    val state: RobotState,
    val missionId: String?,
    val checkpoint: Int,
    val total: Int = Zone.TOTAL,
    val zone: Zone?,
    val moving: Boolean,
    val speedMs: Double,
    val activeAlerts: Int,
    val peopleDetected: Int,
    val zonesVerified: Int,
    val zonesTotal: Int = Zone.TOTAL,
    val zonesStatus: Map<Zone, ZoneStatus>,
    val localizationQuality: LocalizationQuality
)
