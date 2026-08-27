package com.jjwalter.pooltemp.ui.theme

import androidx.compose.ui.graphics.Color

// Pool / heater accent palette. Background is deep navy so the warm amber of
// an ON heater state pops; cool teal for "good" pool readings.
val PoolBackground = Color(0xFF0B1220)
val PoolSurface = Color(0xFF111B2E)
val PoolSurfaceVariant = Color(0xFF1B2942)
val PoolOnSurface = Color(0xFFE2E8F0)
val PoolOnSurfaceMuted = Color(0xFF94A3B8)

val PoolAccent = Color(0xFF22D3EE)        // teal -- water
val PoolAccentDim = Color(0xFF155E75)
val HeaterOn = Color(0xFFF59E0B)          // amber -- matches web UI
val HeaterOff = Color(0xFF475569)
val Danger = Color(0xFFEF4444)

// Water-chemistry status vocabulary. Kept identical to the web chemistry page
// so a reading reads the same on both surfaces: green in range, amber low,
// red high, slate not tested.
val ChemOk = Color(0xFF22C55E)
val ChemLow = Color(0xFFF59E0B)
val ChemHigh = Color(0xFFEF4444)
val ChemUnknown = Color(0xFF475569)
val ChemAccent = Color(0xFFA78BFA)   // violet, matching the web page's domain hue
