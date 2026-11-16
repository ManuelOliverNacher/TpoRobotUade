package com.uade.robotcierre.model

/** Cómo terminó una ronda del historial. */
enum class MissionResult {
    /** La ronda sigue en curso. */
    IN_PROGRESS,
    /** Recorrió las 6 zonas y volvió a la base. */
    COMPLETED,
    /** El operador la canceló. */
    CANCELLED,
    /** Volvió a la base sin terminar el recorrido. */
    RETURNED_EARLY,
    /** La app se cerró con la ronda abierta. */
    INTERRUPTED
}
