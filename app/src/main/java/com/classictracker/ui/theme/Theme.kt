package com.classictracker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Material 3 / Google Inspired Palette ---

// Colors for Dark Theme (Deep & Modern)
val MD3_Dark_Primary = Color(0xFFD0BCFF)
val MD3_Dark_OnPrimary = Color(0xFF381E72)
val MD3_Dark_Secondary = Color(0xFFCCC2DC)
val MD3_Dark_Background = Color(0xFF1C1B1F)
val MD3_Dark_Surface = Color(0xFF25232A)
val MD3_Dark_SurfaceVariant = Color(0xFF49454F)
val MD3_Dark_OnSurface = Color(0xFFE6E1E5)
val MD3_Dark_OnSurfaceVariant = Color(0xFFCAC4D0)
val MD3_Dark_Outline = Color(0xFF938F99)

// Standard Gauge Colors adapted for Dark
val GaugeTeal = Color(0xFF80DEEA)
val GaugeAmber = Color(0xFFFFCC80)
val GaugeRed = Color(0xFFF48FB1)
val TextPrimary = Color(0xFFE6E1E5)
val TextSecondary = Color(0xFFCAC4D0)
val GarageBlack = Color(0xFF1C1B1F)
val GaragePanel = Color(0xFF25232A)
val GarageCard = Color(0xFF49454F)
val Divider = Color(0xFF938F99)

// Colors for Light Theme (Clean Google Style)
val MD3_Light_Primary = Color(0xFF6750A4)
val MD3_Light_OnPrimary = Color(0xFFFFFFFF)
val MD3_Light_Secondary = Color(0xFF625B71)
val MD3_Light_Background = Color(0xFFFEF7FF)
val MD3_Light_Surface = Color(0xFFFFFFFF)
val MD3_Light_SurfaceVariant = Color(0xFFE7E0EC)
val MD3_Light_OnSurface = Color(0xFF1C1B1F)
val MD3_Light_OnSurfaceVariant = Color(0xFF49454F)
val MD3_Light_Outline = Color(0xFF79747E)

private val DarkColorScheme = darkColorScheme(
    primary = MD3_Dark_Primary,
    onPrimary = MD3_Dark_OnPrimary,
    secondary = MD3_Dark_Secondary,
    background = MD3_Dark_Background,
    surface = MD3_Dark_Surface,
    onBackground = MD3_Dark_OnSurface,
    onSurface = MD3_Dark_OnSurface,
    surfaceVariant = MD3_Dark_SurfaceVariant,
    onSurfaceVariant = MD3_Dark_OnSurfaceVariant,
    outline = MD3_Dark_Outline
)

private val LightColorScheme = lightColorScheme(
    primary = MD3_Light_Primary,
    onPrimary = MD3_Light_OnPrimary,
    secondary = MD3_Light_Secondary,
    background = MD3_Light_Background,
    surface = MD3_Light_Surface,
    onBackground = MD3_Light_OnSurface,
    onSurface = MD3_Light_OnSurface,
    surfaceVariant = MD3_Light_SurfaceVariant,
    onSurfaceVariant = MD3_Light_OnSurfaceVariant,
    outline = MD3_Light_Outline
)

@Composable
fun ClassicTrackerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
