package app.claudelimit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.claudelimit.LocalAr
import app.claudelimit.Settings
import app.claudelimit.Tx

// The same green look as Pocketta.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6B4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFA9F0CF), onPrimaryContainer = Color(0xFF002116),
    tertiary = Color(0xFF3D6373), tertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFFF3F6F4), surface = Color(0xFFF3F6F4), surfaceContainer = Color(0xFFFFFFFF),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8DD5B3), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139), onPrimaryContainer = Color(0xFFA9F0CF),
    secondary = Color(0xFFB4CCBF), onSecondary = Color(0xFF20352B),
    secondaryContainer = Color(0xFF364B41), onSecondaryContainer = Color(0xFFD0E8DB),
    tertiary = Color(0xFFA5CCE0), tertiaryContainer = Color(0xFF244C5B), onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF3F4944), onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF3F4944),
    surfaceContainer = Color(0xFF1B211E),
)

val LocalDark = staticCompositionLocalOf { false }

/** Green while there is plenty left, amber past half, red when nearly out. [used] is the percent used. */
@Composable
fun levelColor(used: Double): Color {
    val dark = LocalDark.current
    return when {
        used >= 80.0 -> if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
        used >= 50.0 -> if (dark) Color(0xFFFFB871) else Color(0xFF9A5B00)
        else -> if (dark) Color(0xFF8FD8A4) else Color(0xFF1F7A3F)
    }
}

@Composable
fun ClaudeLimitTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = when (s.theme) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val ar = s.lang == "AR"
    Tx.ar = ar
    CompositionLocalProvider(
        LocalDark provides dark,
        LocalAr provides ar,
        LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
