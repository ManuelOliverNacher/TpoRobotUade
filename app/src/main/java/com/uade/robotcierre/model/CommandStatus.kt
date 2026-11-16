package com.uade.robotcierre.model

/** Estado de una orden: enviarla NO equivale a que se ejecutó. */
enum class CommandStatus { PENDING, CONFIRMED, REJECTED, NO_RESPONSE }
