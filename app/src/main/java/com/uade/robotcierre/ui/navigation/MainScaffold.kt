package com.uade.robotcierre.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText
import com.uade.robotcierre.viewmodel.UiState

/** Las 4 pestañas de la barra inferior. */
enum class MainTab(val label: String, val icon: ImageVector) {
    PANEL("Panel", Icons.Default.Home),
    RONDAS("Rondas", Icons.AutoMirrored.Filled.List),
    ALERTAS("Alertas", Icons.Default.Notifications),
    HISTORIAL("Historial", Icons.Default.DateRange)
}

/** Marco de la app con la barra inferior. La pestaña Alertas muestra un globo con las alertas abiertas. */
@Composable
fun MainScaffold(
    state: UiState,
    tab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (MainTab) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = RobotColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = RobotColors.Surface) {
                MainTab.entries.forEach { item ->
                    val openAlerts = state.activeAlerts.size
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { onTabSelected(item) },
                        icon = {
                            if (item == MainTab.ALERTAS && openAlerts > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = RobotColors.Error) { Text(openAlerts.toString()) }
                                }) { Icon(item.icon, contentDescription = null) }
                            } else {
                                Icon(item.icon, contentDescription = null)
                            }
                        },
                        label = { Text(item.label, style = RobotText.Small) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RobotColors.Primary,
                            selectedTextColor = RobotColors.Primary,
                            indicatorColor = RobotColors.InfoBg,
                            unselectedIconColor = RobotColors.TextSecondary,
                            unselectedTextColor = RobotColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content(tab) }
    }
}
