package com.uade.robotcierre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uade.robotcierre.ui.RobotCierreApp
import com.uade.robotcierre.ui.theme.RobotCierreUadeTheme
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.viewmodel.RobotViewModel

/** Única Activity. Orientación fija en vertical (ver manifest). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Franja de estado en azul primario: íconos claros en toda la app.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(RobotColors.Primary.toArgb()))
        val container = (application as RobotCierreApplication).container
        setContent {
            RobotCierreUadeTheme {
                // El ViewModel se crea una sola vez por Activity y sobrevive a la recreación.
                val factory = remember { container.createRobotViewModelFactory() }
                val viewModel: RobotViewModel = viewModel(factory = factory)
                RobotCierreApp(viewModel)
            }
        }
    }
}
