package com.uade.robotcierre.ui.screens

import com.uade.robotcierre.ui.formatTime
import com.uade.robotcierre.viewmodel.UiState

/** "Actualizado 22:41:05" o "Sin datos todavía". */
fun updatedText(state: UiState): String =
    state.lastUpdateMillis?.let { "Actualizado ${formatTime(it)}" } ?: "Sin datos todavía"
