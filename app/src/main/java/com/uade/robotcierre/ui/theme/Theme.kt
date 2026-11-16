package com.uade.robotcierre.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RobotColorScheme = lightColorScheme(
    primary = RobotColors.Primary,
    onPrimary = Color.White,
    secondary = RobotColors.Navy,
    onSecondary = Color.White,
    background = RobotColors.Background,
    onBackground = RobotColors.Text,
    surface = RobotColors.Surface,
    onSurface = RobotColors.Text,
    surfaceVariant = RobotColors.DisabledBg,
    onSurfaceVariant = RobotColors.TextSecondary,
    outline = RobotColors.Border,
    error = RobotColors.Error,
    onError = Color.White,
    errorContainer = RobotColors.ErrorBg,
    onErrorContainer = RobotColors.Error
)

/** Tema único (claro). La especificación define una sola paleta; no se usa color dinámico. */
@Composable
fun RobotCierreUadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RobotColorScheme,
        typography = Typography,
        content = content
    )
}
