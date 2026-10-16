package com.ronybrand.estados.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Same palette as the web, React Native and Flutter apps. */
object AppColors {
    val Brand = Color(0xFF155E63)
    val Success = Color(0xFF2F7A4F)
    val Danger = Color(0xFFB4432E)
    val DangerSoft = Color(0xFFF7E6E2)
    val Muted = Color(0xFF52606D)
    val Background = Color(0xFFF5F7F8)
}

private val Colors = lightColorScheme(
    primary = AppColors.Brand,
    onPrimary = Color.White,
    error = AppColors.Danger,
    onError = Color.White,
    background = AppColors.Background,
    surface = Color.White,
    surfaceVariant = Color.White,
)

@Composable
fun EstadosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
