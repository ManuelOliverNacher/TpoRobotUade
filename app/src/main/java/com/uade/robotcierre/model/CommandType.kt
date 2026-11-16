package com.uade.robotcierre.model

/** Órdenes que el operador puede enviar al robot. */
enum class CommandType {
    START_MISSION,
    PAUSE_MISSION,
    RESUME_MISSION,
    CANCEL_MISSION,
    RETURN_TO_SAFE_POINT,
    OPERATIONAL_STOP,
    ACK_ALERT,
    MANUAL_REVIEW
}
