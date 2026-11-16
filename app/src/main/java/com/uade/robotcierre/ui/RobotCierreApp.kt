package com.uade.robotcierre.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uade.robotcierre.ui.navigation.MainScaffold
import com.uade.robotcierre.ui.navigation.MainTab
import com.uade.robotcierre.ui.screens.RobotSelectionScreen
import com.uade.robotcierre.viewmodel.RobotViewModel

/**
 * Raíz de la UI: Selección de robot → pantalla principal con 4 pestañas.
 * Solo observa el estado del ViewModel y le envía eventos; nunca toca el simulador.
 */
@Composable
fun RobotCierreApp(viewModel: RobotViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var supervising by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MainTab.PANEL) }

    // Atrás: de otra pestaña vuelve al Panel; del Panel vuelve a la selección de robot.
    BackHandler(enabled = supervising) {
        if (tab != MainTab.PANEL) tab = MainTab.PANEL else supervising = false
    }

    if (!supervising) {
        RobotSelectionScreen(state = state, onSupervise = { supervising = true })
    } else {
        MainScaffold(state = state, tab = tab, onTabSelected = { tab = it })
    }
}
