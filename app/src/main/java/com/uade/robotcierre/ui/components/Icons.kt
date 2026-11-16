package com.uade.robotcierre.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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

    /** Batería (dibujada a mano: no viene en `material-icons-core`). */
    val Battery: ImageVector by lazy {
        ImageVector.Builder("Battery", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Cuerpo con el hueco interior (relleno par-impar) y el borne a la derecha.
                moveTo(3f, 7f); horizontalLineToRelative(15f); verticalLineToRelative(10f)
                horizontalLineToRelative(-15f); close()
                moveTo(5f, 9f); verticalLineToRelative(6f); horizontalLineToRelative(11f)
                verticalLineToRelative(-6f); close()
                moveTo(19f, 10f); horizontalLineToRelative(2f); verticalLineToRelative(4f)
                horizontalLineToRelative(-2f); close()
            }
        }.build()
    }
}
