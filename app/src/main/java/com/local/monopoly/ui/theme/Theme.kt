package com.local.monopoly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MonopolyRed = Color(0xFFD32F2F)
val MonopolyGreen = Color(0xFF1E7D46)
val BoardCream = Color(0xFFEDE8D9)
val InkColor = Color(0xFF17130C)
val PaperColor = Color(0xFFF4F0E4)

@Composable
fun MonopolyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MonopolyRed,
            secondary = MonopolyGreen,
            background = PaperColor,
            surface = Color.White,
            error = Color(0xFFB71C1C)
        ),
        content = content
    )
}
