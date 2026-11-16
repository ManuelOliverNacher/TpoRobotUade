package com.uade.robotcierre.model

/**
 * Los 6 puntos de control de la ronda, en orden de recorrido.
 * [checkpoint] va de 1 a 6; [label] es el texto para mostrar en la UI.
 */
enum class Zone(val checkpoint: Int, val label: String) {
    HALL(1, "Hall"),
    CORRIDOR(2, "Pasillo de servicios"),
    RESTROOM_DOOR(3, "Puerta del baño"),
    PERGOLA(4, "Zona techada del patio"),
    LAWN(5, "Césped con asientos"),
    ESPLANADE(6, "Explanada y rampa");

    companion object {
        const val TOTAL = 6
    }
}
