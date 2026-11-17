package com.uade.robotcierre

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uade.robotcierre.data.AppDatabase
import com.uade.robotcierre.data.RoomHistoryRepository
import com.uade.robotcierre.repository.HistoryRepository
import com.uade.robotcierre.repository.SimulatedRobotRepository
import com.uade.robotcierre.repository.SimulationConfig
import com.uade.robotcierre.viewmodel.RobotViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Contenedor de dependencias (manual). La base Room vive todo el proceso; el simulador del robot
 * vive solo mientras vive el ViewModel: si la app se cierra y se reabre, arranca un robot nuevo
 * en reposo (la ronda que quedó abierta se guarda como INTERRUPTED).
 *
 * @param demoMode si es true se ofrece el panel de simulación (solo para la demostración).
 */
class AppContainer(context: Context, val demoMode: Boolean = true) {

    private val appContext = context.applicationContext

    /** Parámetros del simulador. Solo se cambian en pruebas (por ejemplo, para acelerar una ronda). */
    var simulationConfig: SimulationConfig = SimulationConfig()

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val historyRepository: HistoryRepository by lazy { RoomHistoryRepository(database.historyDao()) }

    /** Borra el historial guardado. Solo para pruebas y depuración. */
    suspend fun clearHistory() = withContext(Dispatchers.IO) { database.clearAllTables() }

    /** Fábrica del [RobotViewModel]. Se pide con `viewModel(factory = …)` una sola vez por Activity. */
    fun createRobotViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            // Los códigos de ronda continúan desde el último guardado en Room. Es una consulta mínima y
            // única, al crear el ViewModel (no se repite): bloquear acá es aceptable y evita sumar un
            // método asíncrono al contrato RobotRepository. El simulador no accede a Room.
            val lastMissionNumber = runBlocking(Dispatchers.IO) { historyRepository.lastMissionNumber() }
            val simulated = SimulatedRobotRepository(scope, simulationConfig, lastMissionNumber = lastMissionNumber)
            RobotViewModel(
                repository = simulated,
                simulation = if (demoMode) simulated else null,
                history = historyRepository,
                historyOnError = { Log.e(TAG, "No se pudo guardar en el historial", it) },
                onDispose = { scope.cancel() }
            )
        }
    }

    companion object {
        const val TAG = "RobotCierre"
    }
}
