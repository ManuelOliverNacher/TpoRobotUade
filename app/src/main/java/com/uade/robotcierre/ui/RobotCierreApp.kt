package com.uade.robotcierre.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.ui.components.ModalConfirmacion
import com.uade.robotcierre.ui.navigation.MainScaffold
import com.uade.robotcierre.ui.navigation.MainTab
import com.uade.robotcierre.ui.screens.AlertsScreen
import com.uade.robotcierre.ui.screens.HistoryScreen
import com.uade.robotcierre.ui.screens.RobotSelectionScreen
import com.uade.robotcierre.ui.screens.RoundsScreen
import com.uade.robotcierre.ui.screens.SimulationSheet
import com.uade.robotcierre.ui.screens.SupervisionPanelScreen
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.viewmodel.RobotUiEvent
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
    var showSimulation by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Aviso breve de las acciones de simulación (por ejemplo, "La próxima orden será rechazada.").
    LaunchedEffect(state.simulationNotice?.id) {
        state.simulationNotice?.let { snackbarHostState.showSnackbar(it.text, duration = SnackbarDuration.Short) }
    }
    // El ícono "Simulación" solo existe en modo demo (cuando el ViewModel recibió el simulador).
    val openSimulation: (() -> Unit)? =
        if (state.simulationAvailable) fun() { showSimulation = true } else null

    // Atrás: de otra pestaña vuelve al Panel; del Panel vuelve a la selección de robot.
    BackHandler(enabled = supervising) {
        if (tab != MainTab.PANEL) tab = MainTab.PANEL else supervising = false
    }

    Column(Modifier.fillMaxSize().background(RobotColors.Background)) {
        // Franja de la barra de estado en azul primario (íconos claros en toda la app).
        Box(
            Modifier
                .fillMaxWidth()
                .background(RobotColors.Primary)
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            if (!supervising) {
                RobotSelectionScreen(state = state, onSupervise = { supervising = true })
            } else {
                MainScaffold(
                    state = state,
                    tab = tab,
                    onTabSelected = { tab = it },
                    snackbarHostState = snackbarHostState
                ) { current ->
                    when (current) {
                        MainTab.PANEL -> SupervisionPanelScreen(
                            state = state,
                            onEvent = viewModel::onEvent,
                            onSeeAlerts = { tab = MainTab.ALERTAS },
                            onOpenSimulation = openSimulation
                        )
                        MainTab.RONDAS -> RoundsScreen(state, viewModel::onEvent, onOpenSimulation = openSimulation)
                        MainTab.ALERTAS -> AlertsScreen(state, viewModel::onEvent, onOpenSimulation = openSimulation)
                        MainTab.HISTORIAL -> HistoryScreen(state, viewModel::onEvent, onOpenSimulation = openSimulation)
                    }
                }
            }
        }
    }

    if (showSimulation && state.simulationAvailable) {
        SimulationSheet(
            state = state,
            onAction = { viewModel.onEvent(RobotUiEvent.Simulate(it)) },
            onDismiss = { showSimulation = false }
        )
    }

    // Modal de confirmación: Cancelar ronda y Detención operativa piden confirmar antes de enviarse.
    state.confirmation?.let { type ->
        val (title, text) = when (type) {
            CommandType.CANCEL_MISSION -> "¿Cancelar la ronda?" to "El robot volverá a la base."
            CommandType.OPERATIONAL_STOP -> "¿Detención operativa?" to
                "Es un comando de software simulado y no reemplaza una parada de emergencia física."
            else -> "¿${type.label()}?" to ""
        }
        ModalConfirmacion(
            title = title,
            text = text,
            onConfirm = { viewModel.onEvent(RobotUiEvent.ConfirmPending) },
            onDismiss = { viewModel.onEvent(RobotUiEvent.DismissConfirmation) }
        )
    }
}
