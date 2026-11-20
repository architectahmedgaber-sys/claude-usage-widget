package app.claudelimit.ui

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import app.claudelimit.LocalAr
import app.claudelimit.Pal
import app.claudelimit.Palettes
import app.claudelimit.Settings
import app.claudelimit.Tx

/** The colours chosen in Settings. The widgets use the very same set. */
val LocalPal = staticCompositionLocalOf { Palettes.get("AMBER") }

private fun schemeFor(p: Pal): ColorScheme {
    val panel = Color(p.bg)
    val page = if (p.dark) lerp(panel, Color.Black, 0.45f) else lerp(panel, Color.Black, 0.03f)
    val card = if (p.dark) panel else lerp(panel, Color.White, 0.55f)
    val ink = Color(p.ink)
    val track = Color(p.track)
    return if (p.dark) {
        darkColorScheme(
            primary = Color(p.good), onPrimary = Color(p.onFill),
            primaryContainer = track, onPrimaryContainer = ink,
            secondaryContainer = track, onSecondaryContainer = ink,
            background = page, onBackground = ink,
            surface = page, onSurface = ink,
            surfaceVariant = track, onSurfaceVariant = Color(p.sub),
            surfaceContainer = card, outline = track, outlineVariant = track,
            error = Color(p.bad),
        )
    } else {
        lightColorScheme(
            primary = Color(p.good), onPrimary = Color(p.onFill),
            primaryContainer = track, onPrimaryContainer = ink,
            secondaryContainer = track, onSecondaryContainer = ink,
            background = page, onBackground = ink,
            surface = page, onSurface = ink,
            surfaceVariant = track, onSurfaceVariant = Color(p.sub),
            surfaceContainer = card, outline = track, outlineVariant = track,
            error = Color(p.bad),
        )
    }
}

/** Fine while there is plenty left, then warning, then nearly out. [used] is the percent used. */
@Composable
fun levelColor(used: Double): Color {
    val p = LocalPal.current
    return Color(
        when {
            used >= 80.0 -> p.bad
            used >= 50.0 -> p.warn
            else -> p.good
        }
    )
}

@Composable
fun ClaudeLimitTheme(s: Settings, content: @Composable () -> Unit) {
    val pal = Palettes.get(s.palette)
    val ar = s.lang == "AR"
    Tx.ar = ar
    // Dark icons in the status bar when the colours are light, and the other way round.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !pal.dark
            c.isAppearanceLightNavigationBars = !pal.dark
        }
    }
    CompositionLocalProvider(
        LocalPal provides pal,
        LocalAr provides ar,
        LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = schemeFor(pal), content = content)
    }
}
