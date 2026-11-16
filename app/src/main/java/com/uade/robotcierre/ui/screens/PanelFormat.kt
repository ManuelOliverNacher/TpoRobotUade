package com.uade.robotcierre.ui.screens

import com.uade.robotcierre.model.Telemetry
import java.util.Locale

private val AR = Locale.forLanguageTag("es-AR")

/** Progreso de la ronda: "Punto 3 de 6 · Zona techada", "Volviendo a la base" o "Sin ronda en curso". */
fun progressText(t: Telemetry): String = when {
    t.checkpoint == 0 -> "Sin ronda en curso"
    t.zone == null -> "Volviendo a la base"
    else -> "Punto ${t.checkpoint} de ${t.total} · ${t.zone.label}"
}

/** Velocidad con coma decimal: "0,8 m/s". */
fun speedText(t: Telemetry): String = String.format(AR, "%.1f m/s", t.speedMs)

fun movementText(t: Telemetry): String = if (t.moving) "Caminando" else "Detenido"

/** Autonomía estimada (valor derivado, no una medición). */
fun estimatedText(t: Telemetry): String = "≈ ${t.estimatedMinutes} min restantes"

fun verifiedText(t: Telemetry): String = "${t.zonesVerified} de ${t.zonesTotal}"
