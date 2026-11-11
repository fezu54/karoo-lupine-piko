package org.happycode.karoo.lupinepiko.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LupinePikoCustomColors(
    val statusGood: Color,
    val statusWarning: Color,
    val statusDanger: Color,
)

val LocalLupinePikoCustomColors = staticCompositionLocalOf {
    LupinePikoCustomColors(
        statusGood = Color(0xFF2E7D32),
        statusWarning = Color(0xFFEF6C00),
        statusDanger = Color(0xFFC62828),
    )
}

val MaterialTheme.lupinePikoColors: LupinePikoCustomColors
    @Composable
    get() = LocalLupinePikoCustomColors.current

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5C2814),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5D9C8),
    onPrimaryContainer = Color(0xFF290E05),
    secondary = Color(0xFF8A4520),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFADCC9),
    onSecondaryContainer = Color(0xFF331405),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1B1A),
    surfaceVariant = Color(0xFFF7EFEA),
    onSurfaceVariant = Color(0xFF52433D),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFE5D5CD),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFEAA074),
    onPrimary = Color(0xFF481D08),
    primaryContainer = Color(0xFF672F15),
    onPrimaryContainer = Color(0xFFFADCC9),
    secondary = Color(0xFFE29868),
    onSecondary = Color(0xFF431E05),
    secondaryContainer = Color(0xFF5D2F10),
    onSecondaryContainer = Color(0xFFFADCC9),
    surface = Color(0xFF181210),
    onSurface = Color(0xFFEDE0DB),
    surfaceVariant = Color(0xFF2A1E1A),
    onSurfaceVariant = Color(0xFFD7C2B9),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9F8C84),
    outlineVariant = Color(0xFF4D3B35),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val customColors = if (darkTheme) {
        LupinePikoCustomColors(
            statusGood = Color(0xFF66BB6A),
            statusWarning = Color(0xFFFFA726),
            statusDanger = Color(0xFFEF5350),
        )
    } else {
        LupinePikoCustomColors(
            statusGood = Color(0xFF2E7D32),
            statusWarning = Color(0xFFEF6C00),
            statusDanger = Color(0xFFC62828),
        )
    }

    CompositionLocalProvider(LocalLupinePikoCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
