package com.kawuxing.cleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF0FA47F)
val BrandDeep = Color(0xFF0A8F72)
val Accent = Color(0xFFFF8A3D)
val Danger = Color(0xFFE5484D)
val Sky = Color(0xFF3B82F6)
val Violet = Color(0xFF8B5CF6)
val Amber = Color(0xFFF59E0B)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5EC),
    onPrimaryContainer = Color(0xFF04352A),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7D6),
    onSecondaryContainer = Color(0xFF4A2200),
    background = Color(0xFFF3F7F5),
    onBackground = Color(0xFF111917),
    surface = Color.White,
    onSurface = Color(0xFF111917),
    surfaceVariant = Color(0xFFE6EEEA),
    onSurfaceVariant = Color(0xFF5A6B65),
    outline = Color(0xFFC9D6D1),
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3AD2A6),
    onPrimary = Color(0xFF00251C),
    primaryContainer = Color(0xFF0C4A3A),
    onPrimaryContainer = Color(0xFFB9F3E0),
    secondary = Color(0xFFFFA76B),
    onSecondary = Color(0xFF3A1A00),
    secondaryContainer = Color(0xFF5A2E00),
    onSecondaryContainer = Color(0xFFFFDCC4),
    background = Color(0xFF0C1210),
    onBackground = Color(0xFFE6EFEA),
    surface = Color(0xFF151D19),
    onSurface = Color(0xFFE6EFEA),
    surfaceVariant = Color(0xFF232F2A),
    onSurfaceVariant = Color(0xFFA8BAB3),
    outline = Color(0xFF3A4842),
    error = Color(0xFFFF6B6B)
)

@Composable
fun KaWuXingTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
