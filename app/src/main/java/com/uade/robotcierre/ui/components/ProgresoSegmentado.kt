package com.uade.robotcierre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uade.robotcierre.ui.theme.RobotColors

/** Barra de [total] segmentos: los primeros [done] están completos. */
@Composable
fun ProgresoSegmentado(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        if (index < done) RobotColors.Primary else RobotColors.DisabledBg,
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}
