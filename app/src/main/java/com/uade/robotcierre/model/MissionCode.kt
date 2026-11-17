package com.uade.robotcierre.model

/**
 * Código de una ronda: `M-001`, `M-002`… (el formato lo define el contrato propuesto por el grupo).
 * Funciones puras, sin acceso a Room ni al simulador.
 */
object MissionCode {
    private val FORMAT = Regex("""M-(\d+)""")

    fun format(number: Int): String = "M-%03d".format(number)

    /** Número de un código (`"M-007"` → 7), o null si no tiene el formato esperado. */
    fun parse(code: String): Int? = FORMAT.matchEntire(code)?.groupValues?.get(1)?.toIntOrNull()

    /** Mayor número entre [codes] (ignora los inválidos); 0 si no hay ninguno. */
    fun lastNumber(codes: List<String>): Int = codes.mapNotNull(::parse).maxOrNull() ?: 0
}
