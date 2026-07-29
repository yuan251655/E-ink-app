package com.einkphoto.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Rose40,
    onPrimary = Color.White,
    primaryContainer = Rose90,
    onPrimaryContainer = Rose10,
    secondary = Plum40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF21133D),
    background = Paper98,
    onBackground = Ink10,
    surface = Paper96,
    onSurface = Ink10,
    surfaceVariant = Color(0xFFF6E8ED),
    onSurfaceVariant = Color(0xFF564149),
    outline = OutlineLight,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Rose80,
    onPrimary = Color(0xFF5E1137),
    primaryContainer = Color(0xFF82234F),
    onPrimaryContainer = Rose90,
    secondary = Plum80,
    onSecondary = Color(0xFF382D5D),
    secondaryContainer = Color(0xFF504573),
    onSecondaryContainer = Color(0xFFE9DDFF),
    background = Night06,
    onBackground = Ink90,
    surface = Night12,
    onSurface = Ink90,
    surfaceVariant = Color(0xFF4B3840),
    onSurfaceVariant = Color(0xFFD8C0C8),
    outline = OutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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

private val LocalSemanticColors = staticCompositionLocalOf {
    AppSemanticColors(
        success = SuccessLight,
        onSuccessContainer = Color(0xFF052112),
        successContainer = Color(0xFFB7F2C8),
        warning = WarningLight,
        onWarningContainer = Color(0xFF291800),
        warningContainer = Color(0xFFFFDFA6),
    )
}

val MaterialTheme.appSemanticColors: AppSemanticColors
    @Composable get() = LocalSemanticColors.current

@Composable
fun EInkPhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semanticColors = if (darkTheme) {
        AppSemanticColors(
            success = SuccessDark,
            onSuccessContainer = Color(0xFFD1FFDB),
            successContainer = Color(0xFF174E30),
            warning = WarningDark,
            onWarningContainer = Color(0xFFFFE0AA),
            warningContainer = Color(0xFF5F3C00),
        )
    } else {
        LocalSemanticColors.current
    }
    // The default state layer is too subtle on the app's pale pink cards.
    // One stronger pink ripple makes every Material button and navigation item
    // acknowledge a tap without changing each screen independently.
    val pressIndication = ripple(color = if (darkTheme) Rose80 else Rose40)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
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
