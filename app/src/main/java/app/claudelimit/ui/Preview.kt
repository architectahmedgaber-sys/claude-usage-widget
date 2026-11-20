package app.claudelimit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import app.claudelimit.Palettes
import app.claudelimit.Settings
import app.claudelimit.Store
import app.claudelimit.StoreState
import app.claudelimit.Tx
import app.claudelimit.core.Limit
import app.claudelimit.core.PaceCalc
import app.claudelimit.widget.WidgetDraw
import app.claudelimit.widget.buildItems
import app.claudelimit.widget.styleFor

/** Draws a widget exactly as the home screen will, so a choice in Settings is seen before leaving the screen. */
@Composable
fun WidgetPreview(kind: String, s: Settings, state: StoreState, wDp: Float, hDp: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val bmp = remember(kind, s, state.body, state.fetchedAtMs, wDp, hDp) {
        val now = System.currentTimeMillis()
        val u = state.usage()
        // Sample numbers when nothing has been read yet, so the choices can still be judged.
        val session = u?.sessionAt(now) ?: Limit(38.0, now + 3L * 3_600_000L + 14L * 60_000L)
        val weekly = u?.weekly ?: Limit(59.0, now + 3L * PaceCalc.DAY_MS)
        val plan = Store.planLabel(state.plan)
        WidgetDraw.render(
            wDp = wDp, hDp = hDp, density = density,
            items = buildItems(kind, session, weekly, s, now),
            style = styleFor(kind, s),
            pal = Palettes.get(s.palette),
            bgPercent = s.bgAlpha,
            header = if (plan.isNotEmpty()) "Claude $plan" else "Claude",
            headerRight = "",
            rtl = Tx.ar,
        ).asImageBitmap()
    }
    Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = modifier.aspectRatio(wDp / hDp),
        contentScale = ContentScale.FillBounds,
    )
}
