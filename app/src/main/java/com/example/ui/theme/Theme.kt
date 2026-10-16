package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = StudioGold,
    onPrimary = Color(0xFF140F03),
    primaryContainer = StudioGoldContainer,
    onPrimaryContainer = OnStudioGoldContainer,
    secondary = CobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = CobaltBlueContainer,
    onSecondaryContainer = OnCobaltBlueContainer,
    tertiary = CrimsonHeart,
    onTertiary = Color.White,
    tertiaryContainer = CrimsonHeartContainer,
    onTertiaryContainer = OnCrimsonHeartContainer,
    background = DarkroomBase,
    onBackground = DarkroomTextPrimary,
    surface = DarkroomSurface,
    onSurface = DarkroomTextPrimary,
    surfaceVariant = DarkroomSurfaceElevated,
    onSurfaceVariant = DarkroomTextSecondary,
    outline = DarkroomSurfaceBorder
)

private val LightColorScheme = lightColorScheme(
    primary = StudioGoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF3D6),
    onPrimaryContainer = Color(0xFF4A3400),
    secondary = CobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = CrimsonHeart,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4E6),
    onTertiaryContainer = Color(0xFF881337),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightSurfaceBorder
)

@Composable
fun FotoSyncTheme(
    darkTheme: Boolean = true, // Default to studio darkroom mode
    dynamicColor: Boolean = false, // Preserve intentional studio aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    FotoSyncTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
