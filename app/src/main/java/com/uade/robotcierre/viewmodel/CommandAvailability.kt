package com.uade.robotcierre.viewmodel

/** Si un comando se puede usar ahora y, si no, el motivo para mostrar en el botón deshabilitado. */
data class CommandAvailability(val enabled: Boolean, val reason: String? = null) {
    companion object {
        val ENABLED = CommandAvailability(enabled = true)
        fun disabled(reason: String) = CommandAvailability(enabled = false, reason = reason)
    }
}
