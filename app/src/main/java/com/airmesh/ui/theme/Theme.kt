package com.airmesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AirMeshDarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = AirMeshBlack,
    primaryContainer = GoldDark,
    onPrimaryContainer = AirMeshWhite,
    secondary = GoldLight,
    onSecondary = AirMeshBlack,
    background = AirMeshBlack,
    onBackground = AirMeshWhite,
    surface = AirMeshSurface,
    onSurface = AirMeshWhite,
    surfaceVariant = AirMeshSurfaceVariant,
    onSurfaceVariant = AirMeshGray,
    outline = AirMeshGray,
    error = androidx.compose.ui.graphics.Color(0xFFCF6679)
)

@Composable
fun AirMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirMeshDarkColorScheme,
        typography = AirMeshTypography,
        content = content
    )
}
