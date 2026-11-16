package com.uade.robotcierre.model

/** Resultado de la verificación de una zona. */
enum class ZoneStatus {
    PENDING,
    FREE,
    PERSON_FOUND,
    NOT_VERIFIABLE,
    MANUAL_REVIEW,
    VERIFIED_MANUALLY
}
