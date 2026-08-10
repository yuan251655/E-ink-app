package com.einkphoto.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.einkphoto.app.ui.components.rememberApplePressIndication

private val LightColors = lightColorScheme(
    primary = RoseAccent,
    onPrimary = Color.White,
    primaryContainer = RoseTint,
    onPrimaryContainer = AppleText,
    secondary = AppleTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = AppleTrack,
    onSecondaryContainer = AppleText,
    tertiary = RoseAccent,
    onTertiary = Color.White,
    background = AppleGround,
    onBackground = AppleText,
    surface = AppleSurface,
    onSurface = AppleText,
    surfaceVariant = AppleSurfaceSoft,
    onSurfaceVariant = AppleTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceBright = AppleSurface,
    surfaceDim = AppleTrack,
    surfaceContainerLowest = AppleSurface,
    surfaceContainerLow = AppleSurface,
    surfaceContainer = AppleSurface,
    surfaceContainerHigh = AppleSurface,
    surfaceContainerHighest = AppleSurface,
    outline = AppleOutline,
    outlineVariant = AppleHairline,
    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF68000A),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = RoseAccentDark,
    onPrimary = Color(0xFF4A1028),
    primaryContainer = RoseTintDark,
    onPrimaryContainer = Color(0xFFFFD9E7),
    secondary = AppleNightTextSecondary,
    onSecondary = Color.Black,
    secondaryContainer = AppleNightSurfaceSoft,
    onSecondaryContainer = AppleNightText,
    tertiary = RoseAccentDark,
    onTertiary = Color(0xFF4A1028),
    background = AppleNightGround,
    onBackground = AppleNightText,
    surface = AppleNightSurface,
    onSurface = AppleNightText,
    surfaceVariant = AppleNightSurfaceSoft,
    onSurfaceVariant = AppleNightTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceBright = AppleNightSurfaceSoft,
    surfaceDim = AppleNightGround,
    surfaceContainerLowest = AppleNightGround,
    surfaceContainerLow = AppleNightSurface,
    surfaceContainer = AppleNightSurface,
    surfaceContainerHigh = AppleNightSurfaceSoft,
    surfaceContainerHighest = AppleNightOutline,
    outline = AppleNightOutline,
    outlineVariant = Color(0x24FFFFFF),
    error = Color(0xFFFF6961),
    onError = Color(0xFF5D0005),
    errorContainer = Color(0xFF7A0010),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

@Immutable
data class AppSemanticColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
)

private val LightSemanticColors = AppSemanticColors(
    success = SuccessLight,
    onSuccessContainer = Color(0xFF0A3215),
    successContainer = Color(0xFFE7F7EB),
    warning = WarningLight,
    onWarningContainer = Color(0xFF3A2800),
    warningContainer = Color(0xFFFFF4D6),
)

private val DarkSemanticColors = AppSemanticColors(
    success = SuccessDark,
    onSuccessContainer = Color(0xFFD1FFDB),
    successContainer = Color(0xFF123D22),
    warning = WarningDark,
    onWarningContainer = Color(0xFFFFF1B8),
    warningContainer = Color(0xFF503F00),
)

private val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

val MaterialTheme.appSemanticColors: AppSemanticColors
    @Composable get() = LocalSemanticColors.current

@Composable
fun EInkPhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val pressIndication = rememberApplePressIndication(if (darkTheme) RoseAccentDark else RoseAccent)
    CompositionLocalProvider(
        LocalSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
        LocalIndication provides pressIndication,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
