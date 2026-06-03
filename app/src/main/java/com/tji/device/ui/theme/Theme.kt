package com.tji.device.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TjiPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F3F8C),
    onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    tertiary = TjiOnline,
    onTertiary = Color(0xFF08230D),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = TjiError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TjiPrimary,
    onPrimary = Color.White,
    primaryContainer = TjiPrimarySoft,
    onPrimaryContainer = TjiPrimaryDark,
    secondary = TjiSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF4FA),
    onSecondaryContainer = Color(0xFF253143),
    tertiary = TjiOnline,
    onTertiary = Color.White,
    tertiaryContainer = TjiSuccessSoft,
    onTertiaryContainer = Color(0xFF135200),
    background = TjiBackground,
    onBackground = TjiTextPrimary,
    surface = TjiSurface,
    onSurface = TjiTextPrimary,
    surfaceVariant = TjiSurfaceSoft,
    onSurfaceVariant = TjiTextSecondary,
    outline = TjiBorder,
    outlineVariant = Color(0xFFF0F3F8),
    error = TjiError,
    onError = Color.White,
    errorContainer = TjiDangerSoft,
    onErrorContainer = Color(0xFFA8071A)
)

@Composable
fun BucketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep app branding stable across Android versions and OEM dynamic palettes.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
