package com.minogatv.box.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Minoga color palette ─────────────────────────────────────────────────────

private val MinogaNeonCyan   = Color(0xFF00E5FF)
private val MinogaElectricBlue = Color(0xFF2979FF)
private val MinogaBackground = Color(0xFF080A10)
private val MinogaSurface    = Color(0xFF121624)
private val MinogaSurface2   = Color(0xFF182035)
private val MinogaOnBg       = Color(0xFFF1F5F9)
private val MinogaOnSurface  = Color(0xFFF1F5F9)
private val MinogaSecondary  = Color(0xFF8E9EB5)
private val MinogaError      = Color(0xFFFF1744)

private val DarkColorScheme = darkColorScheme(
    primary         = MinogaNeonCyan,
    onPrimary       = Color.Black,
    primaryContainer    = MinogaElectricBlue,
    onPrimaryContainer  = Color.White,
    secondary       = MinogaSecondary,
    onSecondary     = Color.Black,
    background      = MinogaBackground,
    onBackground    = MinogaOnBg,
    surface         = MinogaSurface,
    onSurface       = MinogaOnSurface,
    surfaceVariant  = MinogaSurface2,
    onSurfaceVariant = MinogaSecondary,
    error           = MinogaError,
    onError         = Color.White,
)

/**
 * Global Material 3 theme for Minoga TV Box.
 *
 * The app is dark-only (TV + night-mode phone). No light scheme is provided.
 */
@Composable
fun MinogaTVBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
