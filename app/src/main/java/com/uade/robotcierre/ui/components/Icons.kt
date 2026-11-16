package com.uade.robotcierre.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Íconos que no vienen en `material-icons-core` (evita sumar la librería extendida). */
object RobotIcons {
    val Pause: ImageVector by lazy {
        ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(14f)
                horizontalLineToRelative(-4f); close()
                moveTo(14f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(14f)
                horizontalLineToRelative(-4f); close()
            }
        }.build()
    }

    val Stop: ImageVector by lazy {
        ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f); horizontalLineToRelative(12f); verticalLineToRelative(12f)
                horizontalLineToRelative(-12f); close()
            }
        }.build()
    }
}
