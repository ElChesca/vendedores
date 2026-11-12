package com.baboons.vendedorescalle.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = LogicaAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF083A4A),
    onPrimaryContainer = Color(0xFFB8E6F5),
    secondary = LogicaAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF12333C),
    onSecondaryContainer = Color(0xFFC8EAF3),
    tertiary = LogicaAccentSoft,
    background = Graphite,
    onBackground = Color(0xFFF5F5F5),
    surface = GraphiteRaised,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = GraphiteVariant,
    onSurfaceVariant = MutedDark,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFE85D5D),
    onError = Color.White,
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = LogicaAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F1F8),
    onPrimaryContainer = Color(0xFF043444),
    secondary = LogicaAccentSoft,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F6FA),
    onSecondaryContainer = Color(0xFF043444),
    tertiary = LogicaAccent,
    background = Cloud,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1F2),
    onSurfaceVariant = MutedLight,
    outline = Color(0xFFD0D0D0),
    outlineVariant = Color(0xFFE6E6E6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val BondShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun BondTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = BondTypography,
        shapes = BondShapes,
        content = content
    )
}
