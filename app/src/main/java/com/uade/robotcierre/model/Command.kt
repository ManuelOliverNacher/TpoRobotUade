package com.uade.robotcierre.model

/**
 * Orden enviada al robot.
 * @property zone solo se usa en [CommandType.MANUAL_REVIEW] (zona que se revisa).
 */
data class Command(
    val commandId: String,
    val type: CommandType,
    val issuedAt: Long,
    val operatorId: String,
    val expiresInSeconds: Int = 10,
    val zone: Zone? = null
)

/** Respuesta del robot a una [Command]. */
data class CommandResponse(
    val commandId: String,
    val status: CommandStatus,
    val reason: String? = null
)
