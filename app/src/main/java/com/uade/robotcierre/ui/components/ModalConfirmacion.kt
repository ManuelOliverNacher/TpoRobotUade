package com.uade.robotcierre.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors
import com.uade.robotcierre.ui.theme.RobotText

/** Modal para cancelar la ronda o la detención operativa: "Volver" (secundario) y "Confirmar" (rojo). */
@Composable
fun ModalConfirmacion(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Confirmar",
    dismissLabel: String = "Volver"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RobotColors.Surface,
        title = { Text(title, style = RobotText.Title, color = RobotColors.Text) },
        text = { Text(text, style = RobotText.Body, color = RobotColors.Text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RobotColors.Error, contentColor = Color.White)
            ) { Text(confirmLabel, style = RobotText.Subtitle) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) { Text(dismissLabel, style = RobotText.Subtitle, color = RobotColors.Primary) }
        }
    )
}
