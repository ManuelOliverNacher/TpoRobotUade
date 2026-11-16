package com.uade.robotcierre.repository

/**
 * Parámetros del simulador. Todos son valores inventados/derivados por el grupo para la demo,
 * NO son mediciones del robot real.
 */
data class SimulationConfig(
    val robotId: String = "go2-edu-01",
    val tickMillis: Long = 1_000L,
    /** Segundos de traslado hacia cada zona. */
    val travelSeconds: Int = 15,
    /** Segundos de verificación en cada zona. */
    val verifySeconds: Int = 5,
    /** Segundos de regreso a la base. */
    val returnSeconds: Int = 15,
    /** Consumo de batería (% por segundo) en misión o regresando. 135 s ≈ 34 %. */
    val missionDrainPerSecond: Double = 0.25,
    /** Consumo de batería (% por segundo) en reposo, pausado o detenido. */
    val idleDrainPerSecond: Double = 0.02,
    val initialBatteryPercent: Double = 100.0,
    /** Umbral de advertencia de batería baja (propuesto por el grupo). */
    val lowBatteryWarningPercent: Double = 20.0,
    /** Por debajo de este valor no se puede iniciar una ronda (propuesto por el grupo). */
    val minBatteryToStartPercent: Double = 10.0,
    /** Batería a la que salta [SimulationEngine.forceLowBattery]. */
    val forcedLowBatteryPercent: Double = 21.0,
    val walkingSpeedMs: Double = 0.8,
    /** Latencia de respuesta determinista: base + paso * (n % pasos) = 1000..2000 ms. */
    val baseLatencyMs: Long = 1_000L,
    val latencyStepMs: Long = 250L,
    val latencySteps: Int = 5
)
