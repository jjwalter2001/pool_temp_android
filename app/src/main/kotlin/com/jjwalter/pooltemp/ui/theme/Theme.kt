package com.jjwalter.pooltemp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PoolDarkColors = darkColorScheme(
    background = PoolBackground,
    surface = PoolSurface,
    surfaceVariant = PoolSurfaceVariant,
    onBackground = PoolOnSurface,
    onSurface = PoolOnSurface,
    onSurfaceVariant = PoolOnSurfaceMuted,
    primary = PoolAccent,
    onPrimary = PoolBackground,
    secondary = HeaterOn,
    onSecondary = PoolBackground,
    error = Danger,
)

@Composable
fun PoolTempTheme(content: @Composable () -> Unit) {
    // Dark-only on purpose -- the readings + warm heater accent are designed
    // around the navy background. Add a light scheme later if anyone asks.
    MaterialTheme(
        colorScheme = PoolDarkColors,
        typography = PoolTypography,
        content = content,
    )
}
